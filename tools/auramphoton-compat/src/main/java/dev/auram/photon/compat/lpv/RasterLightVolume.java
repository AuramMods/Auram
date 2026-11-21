package dev.auram.photon.compat.lpv;

import dev.auram.photon.compat.mixin.ShaderPackSourceAccess;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.layer.GbufferPrograms;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.uniforms.CameraUniforms;
import net.irisshaders.iris.uniforms.MatrixUniforms;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import static org.lwjgl.opengl.GL41C.*;

public final class RasterLightVolume implements AutoCloseable {
    private static RasterLightVolume active;

    private final IrisRenderingPipeline pipeline;
    private final int side;
    private final LightVolumeTarget[] lights = new LightVolumeTarget[2];
    private final Matrix4f shadowInverse = new Matrix4f();
    private final float[] shadowMatrix = new float[16];
    private final float[] drawMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] camera = new float[3];
    private LightVolumeTarget occupancy;
    private LightVolumeTarget palette;
    private int fullscreenVao;
    private int occupancyProgram;
    private int transportProgram;
    private int modelViewLocation;
    private int regionOffsetLocation;
    private int shadowInverseLocation;
    private int viewInverseLocation;
    private int cameraLocation;
    private int stageLocation;
    private int transportCameraLocation;
    private int transportViewInverseLocation;
    private ProgramUniforms transportUniforms;
    private int published;
    private boolean capturing;
    private boolean capturedTerrain;

    private RasterLightVolume(IrisRenderingPipeline pipeline, int side) {
        this.pipeline = pipeline;
        this.side = side;
    }

    public static RasterLightVolume create(IrisRenderingPipeline pipeline, ProgramSet set) {
        boolean usesRasterVolume = set.getShadow().flatMap(ProgramSource::getVertexSource)
                .filter(source -> source.contains("auram_photon_voxel")).isPresent();
        if (!usesRasterVolume) return null;

        int side = Integer.parseInt(set.getPack().getShaderPackOptions().getOptionValues()
                .getStringValueOrDefault("VOXEL_VOLUME_SIZE"));
        RasterLightVolume volume = new RasterLightVolume(pipeline, side);
        try {
            volume.initialize(set);
            Iris.logger.info("Initialized AuramPhoton raster colored lighting at " + side + "³");
            return volume;
        } catch (RuntimeException | Error failure) {
            volume.close();
            throw failure;
        }
    }

    private void initialize(ProgramSet set) {
        ShaderPackSourceAccess sources = (ShaderPackSourceAccess) set.getPack();
        String transportSource = readSource(sources, "/program/lpv/raster_transport.fsh");
        String paletteSource = readSource(sources, "/program/lpv/raster_palette.fsh");
        transportProgram = LightVolumeShaders.transport(transportSource);
        transportCameraLocation = glGetUniformLocation(transportProgram, "cameraPosition");
        transportViewInverseLocation = glGetUniformLocation(transportProgram, "gbufferModelViewInverse");

        ProgramUniforms.Builder builder = ProgramUniforms.builder("AuramPhoton LPV transport", transportProgram);
        CameraUniforms.addCameraUniforms(builder, pipeline.getFrameUpdateNotifier());
        MatrixUniforms.addMatrixUniforms(builder, set.getPackDirectives());
        transportUniforms = builder.buildUniforms();

        occupancy = LightVolumeTarget.volume(side, true);
        lights[0] = LightVolumeTarget.volume(side, false);
        lights[1] = LightVolumeTarget.volume(side, false);
        palette = LightVolumeTarget.palette();
        fullscreenVao = glGenVertexArrays();

        int paletteProgram = LightVolumeShaders.palette(paletteSource);
        try (DrawState draw = new DrawState(fullscreenVao, false)) {
            for (LightVolumeTarget light : lights) {
                draw.bind(light.framebuffer(), paletteProgram, side, side);
                glClearBufferfv(GL_COLOR, 0, new float[4]);
            }
            draw.bind(palette.framebuffer(), paletteProgram, 48, 2);
            glDrawArrays(GL_TRIANGLES, 0, 3);
        } finally {
            glDeleteProgram(paletteProgram);
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

    public static int activeTexture() {
        return active == null ? 0 : active.lights[active.published].texture();
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

    public static void replayTerrain(int mode, long counts, int type, long indices, int draws, long baseVertices) {
        if (active != null && active.capturing && active.occupancyProgram != 0 && draws > 0) {
            active.replay(mode, counts, type, indices, draws, baseVertices);
        }
    }

    private void begin() {
        capturing = false;
        shadowInverse.set(ShadowRenderer.MODELVIEW).invert().get(shadowMatrix);
        try (DrawState draw = new DrawState(0, false)) {
            draw.bind(occupancy.framebuffer(), transportProgram, side, side);
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
            occupancyProgram = LightVolumeShaders.occupancy(source, side);
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

    private void replay(int mode, long counts, int type, long indices, int draws, long baseVertices) {
        try (DrawState draw = new DrawState(0, true)) {
            draw.bind(occupancy.framebuffer(), occupancyProgram, side, side);
            nglMultiDrawElementsBaseVertex(mode, counts, type, indices, draws, baseVertices);
        }
        if (!capturedTerrain) {
            capturedTerrain = true;
            Iris.logger.info("Captured AuramPhoton voxel occupancy from the Embeddium shadow terrain pass");
        }
    }

    private void propagate() {
        capturing = false;
        int next = published ^ 1;
        int activeUnit = glGetInteger(GL_ACTIVE_TEXTURE);
        try (DrawState draw = new DrawState(fullscreenVao, false);
                TextureBinding previous = TextureBinding.bind(0, GL_TEXTURE_3D, GL_TEXTURE_BINDING_3D, lights[published].texture());
                TextureBinding blocks = TextureBinding.bind(1, GL_TEXTURE_3D, GL_TEXTURE_BINDING_3D, occupancy.texture());
                TextureBinding colors = TextureBinding.bind(2, GL_TEXTURE_2D, GL_TEXTURE_BINDING_2D, palette.texture())) {
            draw.bind(lights[next].framebuffer(), transportProgram, side, side);
            glDrawArraysInstanced(GL_TRIANGLES, 0, 3, side);
        } finally {
            glActiveTexture(activeUnit);
        }
        published = next;
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

    @Override
    public void close() {
        if (active == this) active = null;
        if (occupancy != null) occupancy.close();
        for (LightVolumeTarget light : lights) {
            if (light != null) light.close();
        }
        if (palette != null) palette.close();
        if (fullscreenVao != 0) glDeleteVertexArrays(fullscreenVao);
        if (occupancyProgram != 0) glDeleteProgram(occupancyProgram);
        if (transportProgram != 0) glDeleteProgram(transportProgram);
    }
}
