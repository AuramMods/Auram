package dev.auram.bliss.test;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
abstract class PerfFrameProbe {
    @Inject(method = "finalizeLevelRendering", at = @At("TAIL"))
    private void timeFrames(CallbackInfo callback) throws Exception {
        PerfMetrics.onFrame();
    }
}
