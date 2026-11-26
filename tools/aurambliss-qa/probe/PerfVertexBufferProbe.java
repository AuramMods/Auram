package dev.auram.bliss.test;

import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.auram.bliss.qa.PassMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VertexBuffer.class, remap = false)
abstract class PerfVertexBufferProbe {
    @Inject(method = "m_166882_", at = @At("HEAD"))
    private void countDraw(CallbackInfo callback) {
        PassMetrics.vertexBufferDraw();
    }
}
