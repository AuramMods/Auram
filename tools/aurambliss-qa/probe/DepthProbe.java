package dev.auram.bliss.test;

import java.nio.file.Files;
import java.nio.file.Path;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.compat.dh.DHCompat;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.opengl.GL41C.*;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
abstract class DepthProbe {
    @Shadow private RenderTargets renderTargets;
    @Shadow public abstract DHCompat getDHCompat();

    @Inject(method = "finalizeLevelRendering", at = @At("TAIL"))
    private void sampleDepths(CallbackInfo callback) throws Exception {
        Path request = Path.of("bliss-read-depth");
        if (!Files.exists(request)) return;
        DHCompat dh = getDHCompat();
        int solidTexture = dh.getDepthTexNoTranslucent();
        int frontTexture = dh.getDepthTex();
        if (solidTexture <= 0 || frontTexture <= 0 || solidTexture == frontTexture) return;
        Files.delete(request);
        int binding = glGetInteger(GL_TEXTURE_BINDING_2D);
        int buffer = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        int[] packFields = {GL_PACK_ALIGNMENT, GL_PACK_ROW_LENGTH, GL_PACK_SKIP_ROWS, GL_PACK_SKIP_PIXELS};
        int[] packValues = new int[packFields.length];
        for (int index = 0; index < packFields.length; index++) {
            packValues[index] = glGetInteger(packFields[index]);
            glPixelStorei(packFields[index], index == 0 ? 1 : 0);
        }
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        try {
            glBindTexture(GL_TEXTURE_2D, solidTexture);
            int width = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
            int height = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
            if (width == 0 || height == 0) throw new IllegalStateException("DH has no depth attachment");
            var options = Iris.getCurrentPack().orElseThrow().getShaderPackOptions().getOptionValues();
            float scale = options.getBooleanValueOrDefault("TAA_UPSCALING")
                    ? Float.parseFloat(options.getStringValueOrDefault("SCALE_FACTOR")) : 1.0f;
            int activeWidth = Math.round(width * scale);
            int activeHeight = Math.round(height * scale);
            float[] solid = read(solidTexture, width, height, false);
            float[] front = read(frontTexture, width, height, false);
            float[] near = read(renderTargets.getDepthTextureNoTranslucents().getTextureId(), width, height, false);
            RenderTarget packed = renderTargets.getOrCreate(12);
            for (int texture : new int[] {packed.getMainTexture(), packed.getAltTexture()}) {
                float[] rgba = read(texture, width, height, true);
                int format = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_INTERNAL_FORMAT);
                long dhOpaque = 0, dhTranslucent = 0, exact = 0;
                for (int pixel = 0; pixel < solid.length; pixel++) {
                    if (pixel % width >= activeWidth || pixel / width >= activeHeight) continue;
                    if (near[pixel] == 1.0f) {
                        if (solid[pixel] > 0 && solid[pixel] < 1.0f) dhOpaque++;
                        if (front[pixel] > 0 && front[pixel] < solid[pixel]) dhTranslucent++;
                    }
                    int offset = pixel * 4;
                    if (rgba[offset] == near[pixel] && rgba[offset + 1] == solid[pixel] && rgba[offset + 2] == front[pixel]) {
                        exact++;
                    }
                }
                System.out.printf("QA_EVT {\"event\":\"depth_readback\",\"status\":\"info\",\"context\":{\"width\":%d,\"height\":%d,\"activeWidth\":%d,\"activeHeight\":%d,\"format\":%d,\"dhOpaque\":%d,\"dhTranslucent\":%d,\"exact\":%d,\"gl\":%d}}%n",
                    width, height, activeWidth, activeHeight, format, dhOpaque, dhTranslucent, exact, glGetError());
            }
        } finally {
            glBindTexture(GL_TEXTURE_2D, binding);
            glBindBuffer(GL_PIXEL_PACK_BUFFER, buffer);
            for (int index = 0; index < packFields.length; index++) {
                glPixelStorei(packFields[index], packValues[index]);
            }
        }
    }

    private static float[] read(int texture, int width, int height, boolean color) {
        glBindTexture(GL_TEXTURE_2D, texture);
        if (glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH) != width
                || glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT) != height) {
            throw new IllegalStateException("Texture does not match the DH depth size: " + texture);
        }
        float[] data = new float[Math.multiplyExact(width * height, color ? 4 : 1)];
        glGetTexImage(GL_TEXTURE_2D, 0, color ? GL_RGBA : GL_DEPTH_COMPONENT, GL_FLOAT, data);
        return data;
    }
}
