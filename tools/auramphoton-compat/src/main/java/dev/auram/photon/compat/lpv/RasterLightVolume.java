package dev.auram.photon.compat.lpv;

import dev.auram.photon.compat.gl.DrawState;
import dev.auram.photon.compat.mixin.ShaderPackSourceAccess;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.layer.GbufferPrograms;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.samplers.IrisSamplers;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.uniforms.CameraUniforms;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.MatrixUniforms;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.opengl.GL41C.*;
import static org.lwjgl.opengl.GL43C.*;

public final class RasterLightVolume implements AutoCloseable {
    private static RasterLightVolume active;
    // Iris constructs the pipeline on the render thread. Its constructor mixin claims this allocation.
    private static PreparedMetal prepared;

    private final IrisRenderingPipeline pipeline;
    private final int side;
    private final boolean bliss;
    private final int atlasColumns;
    private final int atlasWidth;
    private final int atlasHeight;
    private final LightVolumeTarget[] lights = new LightVolumeTarget[2];
    private final Matrix4f shadowInverse = new Matrix4f();
    private final float[] shadowMatrix = new float[16];
    private final float[] drawMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] camera = new float[3];
    private final MetalLightVolume metal;
    private LightVolumeTarget occupancy;
    private LightVolumeTarget palette;
    private int fullscreenVao;
    private int occupancyProgram;
    private String entityVoxelSource;
    private boolean blissRawEntityPosition;
    private final float[] chunkOffset = new float[3];
    private final Map<Integer, Integer> entityChunkOffsets = new HashMap<>();
    private final Map<EntityAttributes, EntityReplay> entityPrograms = new HashMap<>();
    private boolean capturedBlissEntityDraw;
    private boolean capturingBlissEntityBatch;
    private int transportProgram;
    private boolean computeTransport;
    private int modelViewLocation;
    private int regionOffsetLocation;
    private int shadowInverseLocation;
    private int viewInverseLocation;
    private int cameraLocation;
    private int stageLocation;
    private int transportCameraLocation;
    private int transportViewInverseLocation;
    private ProgramUniforms transportUniforms;
    private ProgramSamplers transportSamplers;
    private int transportSamplerCount;
    private int published;
    private boolean capturing;
    private boolean capturedTerrain;

    private RasterLightVolume(IrisRenderingPipeline pipeline, int side, MetalLightVolume metal, boolean bliss) {
        this.pipeline = pipeline;
        this.side = side;
        this.bliss = bliss;
        this.metal = metal;
        atlasColumns = atlasColumns(side);
        atlasWidth = atlasColumns * side;
        atlasHeight = atlasHeight(side, atlasColumns);
    }

    private static int atlasColumns(int side) {
        return (int) Math.ceil(Math.sqrt(side));
    }

    private static int atlasHeight(int side, int columns) {
        return ((side + columns - 1) / columns) * side;
    }

    public static boolean usesRasterVolume(ProgramSet set) {
        return set.getShadow().flatMap(ProgramSource::getVertexSource)
                .filter(source -> source.contains("auram_photon_voxel")).isPresent();
    }

    private static int volumeSide(ProgramSet set) {
        return Integer.parseInt(set.getPack().getShaderPackOptions().getOptionValues()
                .getStringValueOrDefault("VOXEL_VOLUME_SIZE"));
    }

    public static IrisRenderingPipeline createMetalPipeline(ProgramSet rasterPrograms, ProgramSet metalPrograms) {
        MetalLightVolume allocation;
        try {
            int side = volumeSide(metalPrograms);
            int columns = atlasColumns(side);
            ShaderPackSourceAccess sources = (ShaderPackSourceAccess) metalPrograms.getPack();
            String reprojectionSource = readSource(sources, "/program/lpv/raster_metadata.fsh");
            allocation = MetalLightVolume.create(side, columns, columns * side, atlasHeight(side, columns), reprojectionSource);
        } catch (IOException | RuntimeException | LinkageError error) {
            Iris.logger.warn("AuramPhoton Metal setup failed. Using OpenGL colored lighting. {}", error.toString());
            return new IrisRenderingPipeline(rasterPrograms);
        }

        PreparedMetal before = prepared;
        PreparedMetal current = new PreparedMetal(metalPrograms, allocation);
        prepared = current;
        try {
            return new IrisRenderingPipeline(metalPrograms);
        } finally {
            boolean unclaimed = prepared == current;
            prepared = before;
            if (unclaimed) allocation.close();
        }
    }

    public static RasterLightVolume create(IrisRenderingPipeline pipeline, ProgramSet set) {
        if (!usesRasterVolume(set)) return null;
        boolean bliss = set.getShadow().flatMap(ProgramSource::getVertexSource)
                .filter(source -> source.contains("blissUnpackMaterial"))
                .isPresent();
        int side = bliss ? 1 << Integer.parseInt(set.getPack().getShaderPackOptions()
                .getOptionValues().getStringValueOrDefault("LPV_SIZE")) : volumeSide(set);
        MetalLightVolume metal = null;
        if (prepared != null && prepared.programs() == set) {
            metal = prepared.allocation();
            prepared = null;
        }
        RasterLightVolume volume = new RasterLightVolume(pipeline, side, metal, bliss);
        try {
            volume.initialize(set);
            Iris.logger.info("Initialized " + (bliss ? "AuramBliss " : "AuramPhoton ")
                    + (volume.computeTransport ? "compute" : volume.metal == null ? "raster" : "Metal")
                    + " colored lighting at " + side + "³");
            return volume;
        } catch (RuntimeException | Error failure) {
            volume.close();
            throw failure;
        }
    }

    private void initialize(ProgramSet set) {
        ShaderPackSourceAccess sources = (ShaderPackSourceAccess) set.getPack();
        String paletteSource = readSource(sources, bliss
                ? "/dimensions/compat/lpv_palette.fsh" : "/program/lpv/raster_palette.fsh");
        if (metal != null) {
            transportProgram = metal.reprojectionProgram();
        } else {
            String transportPath = bliss ? "/dimensions/compat/lpv_transport.fsh" : "/program/lpv/raster_transport.fsh";
            if (bliss && supportsComputeTransport()) {
                try {
                    String transportSource = readSource(sources, "/dimensions/compat/lpv_transport.csh");
                    transportProgram = LightVolumeShaders.computeTransport(transportSource, atlasColumns);
                    computeTransport = true;
                } catch (RuntimeException error) {
                    Iris.logger.warn("AuramBliss compute transport failed to initialize. Using raster propagation.", error);
                }
            }
            if (!computeTransport) transportProgram = LightVolumeShaders.transport(readSource(sources, transportPath), atlasColumns);
        }
        transportCameraLocation = glGetUniformLocation(transportProgram, "cameraPosition");
        transportViewInverseLocation = glGetUniformLocation(transportProgram, "gbufferModelViewInverse");

        ProgramUniforms.Builder builder = ProgramUniforms.builder("AuramPhoton LPV transport", transportProgram);
        CameraUniforms.addCameraUniforms(builder, pipeline.getFrameUpdateNotifier());
        MatrixUniforms.addMatrixUniforms(builder, set.getPackDirectives());
        boolean rfLight = bliss && glGetUniformLocation(transportProgram, "auramRfVolume") >= 0;
        boolean fluidLight = bliss && glGetUniformLocation(transportProgram, "auramTinkersFluidLight") >= 0;
        boolean voxyOccupancy = bliss && glGetUniformLocation(transportProgram, "voxyLpvOccupancy") >= 0;
        transportSamplerCount = (rfLight ? 1 : 0) + (fluidLight ? 1 : 0) + (voxyOccupancy ? 1 : 0);
        if (rfLight || fluidLight || voxyOccupancy) {
            // Reuse AuramCore's Iris texture and uniform registration without a hard mod dependency.
            CommonUniforms.generalCommonUniforms(builder, pipeline.getFrameUpdateNotifier(), set.getPackDirectives());
            SystemTimeUniforms.addSystemTimeUniforms(builder);
            var samplers = ProgramSamplers.builder(transportProgram, Set.of(0, 1, 2));
            IrisSamplers.addCustomTextures(samplers, Object2ObjectMaps.emptyMap());
            transportSamplers = samplers.build();
        }
        transportUniforms = builder.buildUniforms();

        if (metal != null) {
            occupancy = metal.occupancy();
            lights[0] = metal.light(0);
            lights[1] = metal.light(1);
            palette = metal.palette();
        } else if (bliss) {
            occupancy = LightVolumeTarget.blissOccupancy(atlasWidth, atlasHeight);
            lights[0] = LightVolumeTarget.blissVolume(side);
            lights[1] = LightVolumeTarget.blissVolume(side);
            palette = LightVolumeTarget.blissPalette();
        } else {
            occupancy = LightVolumeTarget.occupancy(atlasWidth, atlasHeight);
            lights[0] = LightVolumeTarget.volume(side);
            lights[1] = LightVolumeTarget.volume(side);
            palette = LightVolumeTarget.palette();
        }
        fullscreenVao = glGenVertexArrays();

        int paletteProgram = LightVolumeShaders.palette(paletteSource);
        try (DrawState draw = new DrawState(fullscreenVao, false)) {
            for (LightVolumeTarget light : lights) {
                draw.bind(light.framebuffer(), paletteProgram, metal == null ? side : atlasWidth,
                        metal == null ? side : atlasHeight);
                glClearBufferfv(GL_COLOR, 0, new float[4]);
            }
            draw.bind(palette.framebuffer(), paletteProgram, bliss ? 2048 : 48, bliss ? 1 : 2);
            glDrawArrays(GL_TRIANGLES, 0, 3);
        } finally {
            glDeleteProgram(paletteProgram);
        }
        if (bliss) {
            uploadBlissHandheldPalette();
            if (set.getPack().getShaderPackOptions().getOptionValues().getBooleanValueOrDefault("LPV_ENTITY_LIGHTS")) {
                initializeBlissEntityReplay(sources, set);
            }
        }
    }

    private static boolean supportsComputeTransport() {
        if (Platform.get() != Platform.MACOSX
                || !Boolean.parseBoolean(System.getProperty("auramphoton.bliss.compute", "true"))) return false;
        var functions = GL.getCapabilities();
        if (functions.glDispatchCompute == 0 || functions.glBindImageTexture == 0 || functions.glMemoryBarrier == 0) {
            Iris.logger.info("AuramBliss compute transport functions are unavailable");
            return false;
        }
        int threads = glGetInteger(GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS);
        int sharedBytes = glGetInteger(GL_MAX_COMPUTE_SHARED_MEMORY_SIZE);
        int images = glGetInteger(GL_MAX_COMPUTE_IMAGE_UNIFORMS);
        if (threads < 256 || sharedBytes < 12_000 || images < 1) {
            Iris.logger.info("AuramBliss compute transport resources: {} threads, {} shared bytes, {} images",
                    threads, sharedBytes, images);
            return false;
        }
        return true;
    }

    private void initializeBlissEntityReplay(ShaderPackSourceAccess sources, ProgramSet set) {
        entityVoxelSource = readSource(sources, "/dimensions/compat/lpv_entities.vsh");
        blissRawEntityPosition = set.getShadow().flatMap(ProgramSource::getVertexSource)
                .filter(source -> source.contains("vec3 playerpos = gl_Vertex.xyz")).isPresent();
    }

    private EntityReplay linkEntityReplay(EntityAttributes attributes) {
        int program = LightVolumeShaders.blissEntityOccupancy(entityVoxelSource,
                attributes.position(), attributes.entity(), side, atlasColumns, atlasWidth, atlasHeight);
        glProgramUniform1i(program, glGetUniformLocation(program, "blissRawPosition"), blissRawEntityPosition ? 1 : 0);
        return new EntityReplay(program, glGetUniformLocation(program, "iris_ModelViewMat"),
                glGetUniformLocation(program, "iris_ChunkOffset"), glGetUniformLocation(program, "shadowModelViewInverse"),
                glGetUniformLocation(program, "cameraPosition"), glGetUniformLocation(program, "blissRenderStage"));
    }

    private void uploadBlissHandheldPalette() {
        int activeUnit = glGetInteger(GL_ACTIVE_TEXTURE);
        int packBuffer = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        int unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        ByteBuffer slice = MemoryUtil.memCalloc(side * side * 4);
        try {
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            int[] materials = new int[4096];
            try (TextureBinding binding = TextureBinding.bind(0, GL_TEXTURE_2D, GL_TEXTURE_BINDING_2D, palette.texture())) {
                glGetTexImage(GL_TEXTURE_2D, 0, GL_RG_INTEGER, GL_UNSIGNED_INT, materials);
            }
            for (int index = 0; index < 2048; index++) {
                int emission = materials[index * 2];
                for (int channel = 0; channel < 4; channel++) {
                    slice.put(index * 4 + channel, (byte) (emission >>> (channel * 8)));
                }
            }
            for (LightVolumeTarget light : lights) {
                try (TextureBinding binding = TextureBinding.bind(0, GL_TEXTURE_3D, GL_TEXTURE_BINDING_3D, light.texture())) {
                    glTexSubImage3D(GL_TEXTURE_3D, 0, 0, 0, side, side, side, 1, GL_RGBA, GL_UNSIGNED_BYTE, slice);
                }
            }
        } finally {
            MemoryUtil.memFree(slice);
            glBindBuffer(GL_PIXEL_PACK_BUFFER, packBuffer);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            glActiveTexture(activeUnit);
        }
    }

    private static String readSource(ShaderPackSourceAccess sources, String path) {
        String source = sources.auramPhoton$sourceProvider().apply(AbsolutePackPath.fromAbsolutePath(path));
        if (source == null) throw new IllegalStateException("Missing AuramPhoton shader stage " + path);
        return source;
    }

    public static void select(RasterLightVolume volume) {
        active = volume;
    }

    public static int activeTexture(boolean metal) {
        return active != null && (active.metal != null) == metal ? active.lights[active.published].texture() : 0;
    }

    public static int activeBlissTexture() {
        return active != null && active.bliss ? active.lights[active.published].texture() : 0;
    }

    public static void beginShadowCapture() {
        if (active != null) active.begin();
    }

    public static void finishShadowCapture() {
        if (active != null && active.capturing) active.propagate();
    }

    public static void modelView(Matrix4fc matrix) {
        if (active != null && active.capturing) active.uploadModelView(matrix);
    }

    public static void regionOffset(float x, float y, float z) {
        if (active != null && active.capturing && active.occupancyProgram != 0) {
            glProgramUniform3f(active.occupancyProgram, active.regionOffsetLocation, x, y, z);
        }
    }

    public static void replayTerrain(long counts, int type, long indices, int draws, long baseVertices) {
        if (active != null && active.capturing && active.occupancyProgram != 0 && draws > 0) {
            active.replay(counts, type, indices, draws, baseVertices);
        }
    }

    public static void captureBlissEntityBatch(boolean capture) {
        if (active != null && active.entityVoxelSource != null) active.capturingBlissEntityBatch = capture;
    }

    public static boolean isCapturingBlissEntities() {
        return active != null && active.capturingBlissEntityBatch;
    }

    public static void replayBlissEntities(int shader, int count, int type, int position, int entity, Matrix4fc modelView) {
        active.replayEntities(shader, count, type, new EntityAttributes(position, entity), modelView);
    }

    private void replayEntities(int shader, int count, int type, EntityAttributes attributes, Matrix4fc modelView) {
        if (shader == 0) return;

        EntityReplay replay = entityPrograms.computeIfAbsent(attributes, this::linkEntityReplay);
        glProgramUniformMatrix4fv(replay.program(), replay.modelView(), false, modelView.get(drawMatrix));
        glProgramUniformMatrix4fv(replay.program(), replay.shadowInverse(), false, shadowMatrix);
        glProgramUniform3fv(replay.program(), replay.camera(), camera);
        glProgramUniform1i(replay.program(), replay.renderStage(), GbufferPrograms.getCurrentPhase().ordinal());
        int location = entityChunkOffsets.computeIfAbsent(shader, glProgram -> glGetUniformLocation(glProgram, "iris_ChunkOffset"));
        if (location < 0) {
            glProgramUniform3f(replay.program(), replay.chunkOffset(), 0, 0, 0);
        } else {
            glGetUniformfv(shader, location, chunkOffset);
            glProgramUniform3fv(replay.program(), replay.chunkOffset(), chunkOffset);
        }

        int activeUnit = glGetInteger(GL_ACTIVE_TEXTURE);
        try (DrawState draw = new DrawState(0, true, shader);
                TextureBinding material = TextureBinding.bind(0, GL_TEXTURE_2D, GL_TEXTURE_BINDING_2D, palette.texture())) {
            draw.bind(occupancy.framebuffer(), replay.program(), atlasWidth, atlasHeight);
            glDrawElements(GL_POINTS, count, type, 0L);
        } finally {
            glActiveTexture(activeUnit);
        }
        if (!capturedBlissEntityDraw) {
            capturedBlissEntityDraw = true;
            Iris.logger.info("Captured AuramBliss voxel occupancy from an Oculus shadow entity draw");
        }
    }

    private record EntityAttributes(int position, int entity) {}
    private record EntityReplay(int program, int modelView, int chunkOffset, int shadowInverse, int camera,
            int renderStage) {}

    private void begin() {
        capturing = false;
        capturingBlissEntityBatch = false;
        shadowInverse.set(ShadowRenderer.MODELVIEW).invert().get(shadowMatrix);
        try (DrawState draw = new DrawState(0, false)) {
            draw.bind(occupancy.framebuffer(), transportProgram, atlasWidth, atlasHeight);
            glClearBufferuiv(GL_COLOR, 0, new int[4]);
            transportUniforms.update();
            ProgramUniforms.clearActiveUniforms();
            glGetUniformfv(transportProgram, transportCameraLocation, camera);
            if (transportViewInverseLocation != -1) {
                glGetUniformfv(transportProgram, transportViewInverseLocation, viewMatrix);
            }
        }
        if (occupancyProgram != 0) uploadFrameUniforms();
        capturing = true;
    }

    private void uploadModelView(Matrix4fc matrix) {
        if (occupancyProgram == 0) {
            // Oculus first patches its terrain programs at the start of the terrain pass.
            String source = pipeline.getSodiumTerrainPipeline().getShadowVertexShaderSource().orElseThrow();
            occupancyProgram = LightVolumeShaders.occupancy(source, side, atlasColumns, atlasWidth, atlasHeight);
            modelViewLocation = glGetUniformLocation(occupancyProgram, "iris_ModelViewMatrix");
            regionOffsetLocation = glGetUniformLocation(occupancyProgram, "u_RegionOffset");
            shadowInverseLocation = glGetUniformLocation(occupancyProgram, "shadowModelViewInverse");
            viewInverseLocation = glGetUniformLocation(occupancyProgram, "gbufferModelViewInverse");
            cameraLocation = glGetUniformLocation(occupancyProgram, "cameraPosition");
            stageLocation = glGetUniformLocation(occupancyProgram, "renderStage");
            uploadFrameUniforms();
        }
        glProgramUniformMatrix4fv(occupancyProgram, modelViewLocation, false, matrix.get(drawMatrix));
        glProgramUniform1i(occupancyProgram, stageLocation, GbufferPrograms.getCurrentPhase().ordinal());
    }

    private void uploadFrameUniforms() {
        glProgramUniformMatrix4fv(occupancyProgram, shadowInverseLocation, false, shadowMatrix);
        glProgramUniformMatrix4fv(occupancyProgram, viewInverseLocation, false, viewMatrix);
        glProgramUniform3fv(occupancyProgram, cameraLocation, camera);
    }

    private void replay(long counts, int type, long indices, int draws, long baseVertices) {
        int activeUnit = bliss ? glGetInteger(GL_ACTIVE_TEXTURE) : 0;
        try (DrawState draw = new DrawState(0, true);
                TextureBinding material = bliss ? TextureBinding.bind(0, GL_TEXTURE_2D, GL_TEXTURE_BINDING_2D,
                        palette.texture()) : null) {
            draw.bind(occupancy.framebuffer(), occupancyProgram, atlasWidth, atlasHeight);
            // Each vertex records its classified voxel. Point draws write it directly into the atlas.
            nglMultiDrawElementsBaseVertex(GL_POINTS, counts, type, indices, draws, baseVertices);
        } finally {
            if (bliss) glActiveTexture(activeUnit);
        }
        if (!capturedTerrain) {
            capturedTerrain = true;
            Iris.logger.info("Captured " + (bliss ? "AuramBliss" : "AuramPhoton")
                    + " voxel occupancy from the Embeddium shadow terrain pass");
        }
    }

    private void propagate() {
        capturing = false;
        int next = published ^ 1;
        if (metal != null) {
            try (DrawState draw = new DrawState(fullscreenVao, false)) {
                draw.bind(metal.reprojection().framebuffer(), transportProgram, 4, 1);
                glDrawArrays(GL_TRIANGLES, 0, 3);
            }
            metal.propagate(published);
            published = next;
            return;
        }
        int activeUnit = glGetInteger(GL_ACTIVE_TEXTURE);
        try (TextureBinding previous = TextureBinding.bind(0, GL_TEXTURE_3D, GL_TEXTURE_BINDING_3D, lights[published].texture());
                TextureBinding blocks = TextureBinding.bind(1, GL_TEXTURE_2D, GL_TEXTURE_BINDING_2D, occupancy.texture());
                TextureBinding colors = TextureBinding.bind(2, GL_TEXTURE_2D, GL_TEXTURE_BINDING_2D, palette.texture())) {
            if (computeTransport) {
                int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
                try {
                    glUseProgram(transportProgram);
                    runWithTransportSamplers(next);
                } finally {
                    glUseProgram(previousProgram);
                }
            } else {
                try (DrawState draw = new DrawState(fullscreenVao, false)) {
                    draw.bind(lights[next].framebuffer(), transportProgram, side, side);
                    runWithTransportSamplers(next);
                }
            }
        } finally {
            glActiveTexture(activeUnit);
        }
        published = next;
    }

    private void runWithTransportSamplers(int next) {
        if (transportSamplers == null) {
            runTransport(next);
            return;
        }
        try (TextureBinding first = TextureBinding.bind(3, GL_TEXTURE_3D, GL_TEXTURE_BINDING_3D, 0);
                TextureBinding second = transportSamplerCount >= 2
                        ? TextureBinding.bind(4, GL_TEXTURE_3D, GL_TEXTURE_BINDING_3D, 0) : null;
                TextureBinding third = transportSamplerCount >= 3
                        ? TextureBinding.bind(5, GL_TEXTURE_3D, GL_TEXTURE_BINDING_3D, 0) : null) {
            transportSamplers.update();
            runTransport(next);
        } finally {
            transportSamplers.removeListeners();
        }
    }

    private void runTransport(int next) {
        if (!computeTransport) {
            glDrawArraysInstanced(GL_TRIANGLES, 0, 3, side);
            return;
        }
        int image = glGetIntegeri(GL_IMAGE_BINDING_NAME, 0);
        int level = glGetIntegeri(GL_IMAGE_BINDING_LEVEL, 0);
        boolean layered = glGetIntegeri(GL_IMAGE_BINDING_LAYERED, 0) != 0;
        int layer = glGetIntegeri(GL_IMAGE_BINDING_LAYER, 0);
        int access = glGetIntegeri(GL_IMAGE_BINDING_ACCESS, 0);
        int format = glGetIntegeri(GL_IMAGE_BINDING_FORMAT, 0);
        try {
            glBindImageTexture(0, lights[next].texture(), 0, true, 0, GL_WRITE_ONLY, GL_RGBA8);
            glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
            glDispatchCompute(side / 8, side / 8, side / 4);
            glMemoryBarrier(GL_TEXTURE_FETCH_BARRIER_BIT | GL_TEXTURE_UPDATE_BARRIER_BIT | GL_FRAMEBUFFER_BARRIER_BIT);
        } finally {
            glBindImageTexture(0, image, level, layered, layer, access, format);
        }
    }

    private record TextureBinding(int unit, int target, int previousTexture, int previousSampler) implements AutoCloseable {
        private static TextureBinding bind(int unit, int target, int binding, int texture) {
            glActiveTexture(GL_TEXTURE0 + unit);
            TextureBinding previous = new TextureBinding(unit, target, glGetInteger(binding), glGetInteger(GL_SAMPLER_BINDING));
            glBindTexture(target, texture);
            glBindSampler(unit, 0);
            return previous;
        }

        @Override
        public void close() {
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(target, previousTexture);
            glBindSampler(unit, previousSampler);
        }
    }

    private record PreparedMetal(ProgramSet programs, MetalLightVolume allocation) {}

    @Override
    public void close() {
        if (active == this) active = null;
        if (metal != null) {
            metal.close();
        } else {
            if (occupancy != null) occupancy.close();
            for (LightVolumeTarget light : lights) {
                if (light != null) light.close();
            }
            if (palette != null) palette.close();
            if (transportProgram != 0) glDeleteProgram(transportProgram);
        }
        if (fullscreenVao != 0) glDeleteVertexArrays(fullscreenVao);
        if (occupancyProgram != 0) glDeleteProgram(occupancyProgram);
        for (EntityReplay replay : entityPrograms.values()) glDeleteProgram(replay.program());
    }
}
