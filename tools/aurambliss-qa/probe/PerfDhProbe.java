package dev.auram.bliss.test;

import dev.auram.bliss.qa.PassMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.render.renderer.LodRenderer", remap = false)
abstract class PerfDhProbe {
    @Unique private PassMetrics.Span auramDhSpan;
    @Unique private PassMetrics.Span auramDhDeferredSpan;

    @Inject(method = "render", at = @At("HEAD"))
    private void beginDh(CallbackInfo callback) {
        auramDhSpan = PassMetrics.begin("stage/dh");
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void endDh(CallbackInfo callback) {
        PassMetrics.end(auramDhSpan);
    }

    @Inject(method = "renderDeferred", at = @At("HEAD"))
    private void beginDhDeferred(CallbackInfo callback) {
        auramDhDeferredSpan = PassMetrics.begin("stage/dh-deferred");
    }

    @Inject(method = "renderDeferred", at = @At("RETURN"))
    private void endDhDeferred(CallbackInfo callback) {
        PassMetrics.end(auramDhDeferredSpan);
    }
}
