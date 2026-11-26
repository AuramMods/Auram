package dev.auram.bliss.test;

import dev.auram.bliss.qa.PerfMetrics;
import dev.auram.bliss.qa.PassMetrics;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
abstract class PerfFrameProbe {
    @Unique private PassMetrics.Span auramShadowSpan;

    @Inject(method = "beginLevelRendering", at = @At("HEAD"))
    private void beginGpuFrame(CallbackInfo callback) {
        PassMetrics.beginFrame();
    }

    @Inject(method = "renderShadows", at = @At("HEAD"))
    private void beginShadows(CallbackInfo callback) {
        PassMetrics.renderingShadows(true);
        auramShadowSpan = PassMetrics.begin("stage/shadows");
    }

    @Inject(method = "renderShadows", at = @At("RETURN"))
    private void endShadows(CallbackInfo callback) {
        PassMetrics.end(auramShadowSpan);
        PassMetrics.renderingShadows(false);
    }

    @Inject(method = "finalizeLevelRendering", at = @At("TAIL"))
    private void timeFrames(CallbackInfo callback) throws Exception {
        PassMetrics.endFrame();
        PerfMetrics.onFrame();
    }
}
