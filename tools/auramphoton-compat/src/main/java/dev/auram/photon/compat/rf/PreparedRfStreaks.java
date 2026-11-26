package dev.auram.photon.compat.rf;

import dev.auram.photon.compat.mixin.ShaderPackSourceAccess;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.samplers.IrisSamplers;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;

import static org.lwjgl.opengl.GL43C.*;

public final class PreparedRfStreaks implements AutoCloseable {
    private static final int BUFFER_BINDING = 15;
    private static final int MIN_SHARED_EDGES = 64;
    private static final int MAX_SCAN_CELLS = 8192;
    // A cursor, the 64³ cell directory, then 8,192 std430 pulse records at a 112-byte stride.
    private static final int BUFFER_BYTES = 1_048_592 + 8_192 * 112;
    private static PreparedRfStreaks active;

    private final Field fieldActive;
    private final Map<?, ?> edges;
    private final Vector3f minimum;
    private final Vector3f maximum;
    private final int program;
    private final int buffer;
    private final ProgramUniforms uniforms;
    private final ProgramSamplers samplers;

    private int previousBinding;
    private long previousOffset;
    private long previousSize;
    private boolean prepared;
    private boolean firstDispatch = true;

    private PreparedRfStreaks(IrisRenderingPipeline pipeline, ProgramSet programs, Field fieldActive, Map<?, ?> edges,
                              Vector3f minimum, Vector3f maximum, int program, int buffer) {
        this.fieldActive = fieldActive;
        this.edges = edges;
        this.minimum = minimum;
        this.maximum = maximum;
        this.program = program;
        this.buffer = buffer;
        var uniformBuilder = ProgramUniforms.builder("AuramBliss RF preparation", program);
        CommonUniforms.generalCommonUniforms(uniformBuilder, pipeline.getFrameUpdateNotifier(), programs.getPackDirectives());
        SystemTimeUniforms.addSystemTimeUniforms(uniformBuilder);
        uniforms = uniformBuilder.buildUniforms();
        var samplerBuilder = ProgramSamplers.builder(program, Set.of());
        IrisSamplers.addCustomTextures(samplerBuilder, Object2ObjectMaps.emptyMap());
        samplers = samplerBuilder.build();
    }

    public static boolean requested() {
        if (!Boolean.parseBoolean(System.getProperty("auramphoton.bliss.prepareRf", "true"))) return false;
        var caps = GL.getCapabilities();
        return caps.glDispatchCompute != 0 && caps.glMemoryBarrier != 0 && caps.glBindBufferBase != 0
                && glGetInteger(GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS) > BUFFER_BINDING
                && glGetInteger(GL_MAX_FRAGMENT_SHADER_STORAGE_BLOCKS) >= 1
                && glGetInteger64(GL_MAX_SHADER_STORAGE_BLOCK_SIZE) >= BUFFER_BYTES;
    }

    public static PreparedRfStreaks create(IrisRenderingPipeline pipeline, ProgramSet programs) {
        if (!requested()) return null;
        var sources = (ShaderPackSourceAccess) programs.getPack();
        String source = sources.auramPhoton$sourceProvider()
                .apply(AbsolutePackPath.fromAbsolutePath("/dimensions/compat/rf_prepare.csh"));
        if (source == null || !source.contains("local_size_x")) return null;
        int program = 0;
        int buffer = 0;
        try {
            // AuramCore publishes RF only when an active field is available for this frame.
            var fieldTexture = Class.forName("art.arcane.auram.rf.client.RfFieldTexture");
            var field = accessibleField(fieldTexture, "active");
            var presentation = accessibleField(fieldTexture, "presentation").get(null);
            var edges = (Map<?, ?>) accessibleField(presentation.getClass(), "tracks").get(presentation);
            var minimum = (Vector3f) accessibleField(fieldTexture, "minimum").get(null);
            var maximum = (Vector3f) accessibleField(fieldTexture, "maximum").get(null);
            program = compile(source);
            buffer = glGenBuffers();
            int previous = glGetInteger(GL_SHADER_STORAGE_BUFFER_BINDING);
            try {
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffer);
                glBufferData(GL_SHADER_STORAGE_BUFFER, BUFFER_BYTES, GL_DYNAMIC_DRAW);
            } finally {
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, previous);
            }
            Iris.logger.info("Initialized AuramBliss RF preparation");
            return new PreparedRfStreaks(pipeline, programs, field, edges, minimum, maximum, program, buffer);
        } catch (RuntimeException | ReflectiveOperationException error) {
            if (buffer != 0) glDeleteBuffers(buffer);
            if (program != 0) glDeleteProgram(program);
            Iris.logger.warn("AuramBliss RF preparation failed to initialize. Using inline RF streaks.", error);
            return null;
        }
    }

    private static Field accessibleField(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static int compile(String source) {
        int shader = glCreateShader(GL_COMPUTE_SHADER);
        int program = 0;
        try {
            glShaderSource(shader, "#version 430 core\n" + source);
            glCompileShader(shader);
            if (glGetShaderi(shader, GL_COMPILE_STATUS) != GL_TRUE) {
                throw new IllegalStateException(glGetShaderInfoLog(shader));
            }
            program = glCreateProgram();
            glAttachShader(program, shader);
            glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) != GL_TRUE) {
                throw new IllegalStateException(glGetProgramInfoLog(program));
            }
            return program;
        } catch (RuntimeException | Error failure) {
            if (program != 0) glDeleteProgram(program);
            throw failure;
        } finally {
            glDeleteShader(shader);
        }
    }

    public static boolean isPrepared() {
        return active != null && active.prepared;
    }

    public void prepareFrame() {
        active = this;
        // A handful of routes do too little repeated fragment work to pay for a dispatch.
        if (edges.size() < MIN_SHARED_EDGES || !hasRf()) return;
        // The one-workgroup scan amortizes well over a local circuit. Widely separated
        // routes leave mostly empty texels and continue to use the per-pixel path.
        float scanCells = (maximum.x - minimum.x) * (maximum.y - minimum.y) * (maximum.z - minimum.z);
        if (scanCells > MAX_SCAN_CELLS) return;
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int previousUnit = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_3D);
        int previousSampler = glGetInteger(GL_SAMPLER_BINDING);
        try {
            glUseProgram(program);
            uniforms.update();
            samplers.update();
            bindCache();
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
            glDispatchCompute(1, 1, 1);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
            if (firstDispatch) {
                Iris.logger.info("Dispatched AuramBliss RF preparation");
                firstDispatch = false;
            }
        } finally {
            ProgramUniforms.clearActiveUniforms();
            ProgramSamplers.clearActiveSamplers();
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_3D, previousTexture);
            glBindSampler(0, previousSampler);
            glActiveTexture(previousUnit);
            glUseProgram(previousProgram);
        }
    }

    private boolean hasRf() {
        try {
            return fieldActive.getBoolean(null);
        } catch (IllegalAccessException error) {
            return false;
        }
    }

    private void bindCache() {
        previousBinding = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, BUFFER_BINDING);
        if (previousBinding != 0) {
            previousOffset = glGetInteger64i(GL_SHADER_STORAGE_BUFFER_START, BUFFER_BINDING);
            previousSize = glGetInteger64i(GL_SHADER_STORAGE_BUFFER_SIZE, BUFFER_BINDING);
        } else {
            previousSize = 0;
        }
        int previous = glGetInteger(GL_SHADER_STORAGE_BUFFER_BINDING);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, BUFFER_BINDING, buffer);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, previous);
        prepared = true;
    }

    public void finishFrame() {
        if (active == this) active = null;
        if (!prepared) return;
        int previous = glGetInteger(GL_SHADER_STORAGE_BUFFER_BINDING);
        if (previousSize > 0) {
            glBindBufferRange(GL_SHADER_STORAGE_BUFFER, BUFFER_BINDING, previousBinding, previousOffset, previousSize);
        } else {
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, BUFFER_BINDING, previousBinding);
        }
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, previous);
        prepared = false;
    }

    @Override
    public void close() {
        finishFrame();
        glDeleteProgram(program);
        glDeleteBuffers(buffer);
    }
}
