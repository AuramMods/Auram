package dev.auram.bliss.test;

import dev.auram.bliss.qa.PassMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.VoxyRenderSystem", remap = false)
abstract class PerfVoxyProbe {
    @Unique private PassMetrics.Span auramVoxyShadowSpan;

    @Inject(method = "renderShadowCasters", at = @At("HEAD"), require = 0)
    private void beginVoxyShadows(CallbackInfo callback) {
        auramVoxyShadowSpan = PassMetrics.begin("stage/voxy-shadows");
    }

    @Inject(method = "renderShadowCasters", at = @At("RETURN"), require = 0)
    private void endVoxyShadows(CallbackInfo callback) {
        PassMetrics.end(auramVoxyShadowSpan);
    }
}
