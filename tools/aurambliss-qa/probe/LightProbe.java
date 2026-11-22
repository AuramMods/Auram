package dev.auram.bliss.test;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import static org.lwjgl.opengl.GL41C.*;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
abstract class LightProbe {
    @Inject(method = "finalizeLevelRendering", at = @At("TAIL"))
    private void sampleLight(CallbackInfo callback) throws Exception {
        if (!Files.deleteIfExists(Path.of("bliss-read-light"))) return;
        Class<?> lightVolume = Class.forName("dev.auram.photon.compat.lpv.RasterLightVolume");
        int texture = (int) lightVolume.getMethod("activeBlissTexture").invoke(null);
        if (texture <= 0) throw new IllegalStateException("Bliss did not publish a light texture");
        int binding = glGetInteger(GL_TEXTURE_BINDING_3D);
        int buffer = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        ByteBuffer data = null;
        try {
            glBindTexture(GL_TEXTURE_3D, texture);
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
            int side = glGetTexLevelParameteri(GL_TEXTURE_3D, 0, GL_TEXTURE_WIDTH);
            int slices = glGetTexLevelParameteri(GL_TEXTURE_3D, 0, GL_TEXTURE_DEPTH);
            int volume = side * side * (slices - 1);
            int palette = side * side;
            data = MemoryUtil.memAlloc((volume + palette) * 4);
            glGetTexImage(GL_TEXTURE_3D, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
            int occupied = 0, emitting = 0, paletteEmitting = 0;
            for (int i = 0; i < volume; i++) {
                int p = i * 4, maximum = Math.max(data.get(p)&255, Math.max(data.get(p+1)&255, data.get(p+2)&255));
                if (maximum > 0) occupied++;
                if (maximum >= 235) emitting++;
            }
            for (int i = volume; i < volume + palette; i++) {
                if (data.get(i * 4 + 3) != 0) paletteEmitting++;
            }
            System.out.printf("QA_EVT {\"event\":\"light_readback\",\"status\":\"info\",\"context\":{\"side\":%d,\"slices\":%d,\"occupied\":%d,\"emitting\":%d,\"paletteEmitting\":%d,\"gl\":%d}}%n",
                    side, slices, occupied, emitting, paletteEmitting, glGetError());
        } finally {
            if (data != null) MemoryUtil.memFree(data);
            glBindTexture(GL_TEXTURE_3D, binding);
            glBindBuffer(GL_PIXEL_PACK_BUFFER, buffer);
        }
    }
}
