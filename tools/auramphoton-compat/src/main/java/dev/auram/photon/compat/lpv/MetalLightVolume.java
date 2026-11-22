package dev.auram.photon.compat.lpv;

import net.irisshaders.iris.Iris;
import org.lwjgl.system.Platform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.lwjgl.opengl.GL41C.*;

public final class MetalLightVolume implements AutoCloseable {
    private static final boolean TRACE = Boolean.getBoolean("auramphoton.metal.trace");
    private static boolean loaded;

    private final long handle;
    private final LightVolumeTarget[] targets;
    private final int reprojectionProgram;
    private final float[] elapsed = new float[3];
    private int frame;

    private MetalLightVolume(long handle, LightVolumeTarget[] targets, int reprojectionProgram) {
        this.handle = handle;
        this.targets = targets;
        this.reprojectionProgram = reprojectionProgram;
    }

    public static boolean requested() {
        return Boolean.getBoolean("auramphoton.metal")
                && Platform.get() == Platform.MACOSX
                && Platform.getArchitecture() == Platform.Architecture.ARM64;
    }

    private static void loadLibrary() throws IOException {
        if (loaded) return;
        String override = System.getProperty("auramphoton.metal.library");
        if (override != null) {
            System.load(Path.of(override).toAbsolutePath().toString());
        } else {
            try (InputStream library = openResource("libauramphoton-metal.dylib")) {
                Path binary = Files.createTempFile("auramphoton-metal-", ".dylib");
                binary.toFile().deleteOnExit();
                Files.copy(library, binary, StandardCopyOption.REPLACE_EXISTING);
                System.load(binary.toString());
            }
        }
        loaded = true;
    }

    private static InputStream openResource(String name) throws IOException {
        InputStream resource = MetalLightVolume.class.getResourceAsStream(name);
        if (resource == null) throw new IOException("Missing bundled AuramPhoton resource " + name);
        return resource;
    }

    static MetalLightVolume create(int side, int columns, int width, int height, String reprojectionSource)
            throws IOException {
        loadLibrary();
        String kernel;
        try (InputStream source = openResource("transport.metal")) {
            kernel = new String(source.readAllBytes(), StandardCharsets.UTF_8);
        }
        int[] textures = new int[5];
        LightVolumeTarget[] targets = new LightVolumeTarget[textures.length];
        glGenTextures(textures);
        long handle = 0;
        int program = 0;
        try {
            handle = createNativeWithTileReset(kernel, side, columns, width, height, textures);
            if (handle == 0) throw new IllegalStateException("AuramPhoton could not create the Metal light volume");
            for (int i = 0; i < textures.length; i++) {
                targets[i] = LightVolumeTarget.rectangle(textures[i]);
            }
            program = LightVolumeShaders.palette(reprojectionSource);
            return new MetalLightVolume(handle, targets, program);
        } catch (RuntimeException | Error e) {
            if (program != 0) glDeleteProgram(program);
            for (LightVolumeTarget target : targets) {
                if (target != null) glDeleteFramebuffers(target.framebuffer());
            }
            glDeleteTextures(textures);
            if (handle != 0) destroyNative(handle);
            throw e;
        }
    }

    LightVolumeTarget occupancy() {
        return targets[0];
    }

    LightVolumeTarget palette() {
        return targets[1];
    }

    LightVolumeTarget reprojection() {
        return targets[2];
    }

    int reprojectionProgram() {
        return reprojectionProgram;
    }

    LightVolumeTarget light(int slot) {
        return targets[3 + slot];
    }

    void propagate(int previous) {
        boolean measure = TRACE && ++frame % 120 == 0;
        propagateNative(handle, previous, measure ? elapsed : null);
        if (measure) {
            Iris.logger.info("[metal-lpv] OpenGL drain {} ms, compute {} ms, Metal wait {} ms",
                    elapsed[0], elapsed[1], elapsed[2]);
        }

        // Rebind the IOSurface texture after the Metal command has completed.
        int newLight = light(previous ^ 1).texture();
        int bound = glGetInteger(GL_TEXTURE_BINDING_RECTANGLE);
        if (bound == newLight) glBindTexture(GL_TEXTURE_RECTANGLE, 0);
        glBindTexture(GL_TEXTURE_RECTANGLE, newLight);
        if (bound != newLight) glBindTexture(GL_TEXTURE_RECTANGLE, bound);
    }

    @Override
    public void close() {
        for (LightVolumeTarget target : targets) target.close();
        glDeleteProgram(reprojectionProgram);
        destroyNative(handle);
    }

    private static native long createNativeWithTileReset(String kernel, int side, int columns, int width, int height, int[] textures);
    private static native void propagateNative(long handle, int previous, float[] trace);
    private static native void destroyNative(long handle);
}
