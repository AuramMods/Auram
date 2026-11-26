package dev.auram.bliss.test;

import dev.auram.bliss.qa.RfPreparedReadback;
import java.nio.file.Files;
import java.nio.file.Path;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import static org.lwjgl.opengl.GL41C.*;

@Mixin(value = IrisRenderingPipeline.class, priority = 900, remap = false)
abstract class RfVolumeProbe {
    @Inject(method = "beginLevelRendering", at = @At("HEAD"))
    private void beginPreparedReadback(CallbackInfo callback) throws Exception {
        RfPreparedReadback.begin();
    }

    @Inject(method = "finalizeLevelRendering", at = @At("TAIL"))
    private void readRfVolume(CallbackInfo callback) throws Exception {
        RfPreparedReadback.finish();
        if (!Files.deleteIfExists(Path.of("bliss-read-rf-volume"))) return;
        var source = Class.forName("art.arcane.auram.rf.client.RfFieldTexture");
        var activeField = source.getDeclaredField("active");
        activeField.setAccessible(true);
        boolean active = activeField.getBoolean(null);
        int cells = 0;
        int faces = 0;
        if (active) {
            int texture = (int) source.getMethod("texture").invoke(null);
            int binding = glGetInteger(GL_TEXTURE_BINDING_3D);
            int buffer = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
            int[] fields = {GL_PACK_ALIGNMENT, GL_PACK_ROW_LENGTH, GL_PACK_IMAGE_HEIGHT, GL_PACK_SKIP_ROWS,
                    GL_PACK_SKIP_PIXELS, GL_PACK_SKIP_IMAGES};
            int[] previous = new int[fields.length];
            for (int i = 0; i < fields.length; i++) {
                previous[i] = glGetInteger(fields[i]);
                glPixelStorei(fields[i], i == 0 ? 1 : 0);
            }
            try {
                glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
                glBindTexture(GL_TEXTURE_3D, texture);
                int width = glGetTexLevelParameteri(GL_TEXTURE_3D, 0, GL_TEXTURE_WIDTH);
                int height = glGetTexLevelParameteri(GL_TEXTURE_3D, 0, GL_TEXTURE_HEIGHT);
                int depth = glGetTexLevelParameteri(GL_TEXTURE_3D, 0, GL_TEXTURE_DEPTH);
                int[] texels = new int[Math.multiplyExact(Math.multiplyExact(width, height), Math.multiplyExact(depth, 4))];
                glGetTexImage(GL_TEXTURE_3D, 0, GL_RGBA_INTEGER, GL_UNSIGNED_INT, texels);
                int[] low = bound(source, "minimum");
                int[] high = bound(source, "maximum");
                for (int z = low[2]; z < high[2]; z++) {
                    for (int y = low[1]; y < high[1]; y++) {
                        for (int x = low[0]; x < high[0]; x++) {
                            int i = 4 * (x + width * (y + height * z));
                            int a = texels[i], b = texels[i + 1], c = texels[i + 2];
                            if ((a | b | c) == 0) continue;
                            cells++;
                            faces += countRates(a) + countRates(b) + countRates(c);
                        }
                    }
                }
            } finally {
                glBindTexture(GL_TEXTURE_3D, binding);
                glBindBuffer(GL_PIXEL_PACK_BUFFER, buffer);
                for (int i = 0; i < fields.length; i++) glPixelStorei(fields[i], previous[i]);
            }
        }
        System.out.printf("QA_EVT {\"event\":\"rf_volume_readback\",\"status\":\"info\",\"context\":{\"active\":%b,\"cells\":%d,\"rates\":%d,\"gl\":%d}}%n",
                active, cells, faces, glGetError());
    }

    private static int[] bound(Class<?> source, String name) throws ReflectiveOperationException {
        var field = source.getDeclaredField(name);
        field.setAccessible(true);
        Object vector = field.get(null);
        var type = vector.getClass();
        return new int[] {(int) type.getField("x").getFloat(vector), (int) type.getField("y").getFloat(vector),
                (int) type.getField("z").getFloat(vector)};
    }

    private static int countRates(int packed) {
        int count = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            if ((packed & (255 << shift)) != 0) count++;
        }
        return count;
    }
}
