package dev.auram.photon.compat.mixin;

import dev.auram.photon.compat.AutomaticFocusSampler;
import net.irisshaders.iris.compat.dh.DHCompat;
import net.irisshaders.iris.pathways.CenterDepthSampler;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
abstract class IrisPipelineFocusMixin {
    @Shadow @Final private CenterDepthSampler centerDepthSampler;
    @Shadow @Final private RenderTargets renderTargets;
    @Shadow public abstract DHCompat getDHCompat();

    @Redirect(method = "beginHand", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/pathways/CenterDepthSampler;sampleCenterDepth()V"))
    private void auramPhoton$sampleVanillaFocus(CenterDepthSampler sampler) {
        if (!((AutomaticFocusSampler)(Object)sampler).auramPhoton$usesAutomaticFocus()) {
            sampler.sampleCenterDepth();
        }
    }

    @Inject(method = "finalizeLevelRendering", at = @At("HEAD"))
    private void auramPhoton$sampleCompleteDepth(CallbackInfo callback) {
        AutomaticFocusSampler sampler = (AutomaticFocusSampler)(Object)centerDepthSampler;
        if (sampler.auramPhoton$usesAutomaticFocus()) {
            // The complete DH depth includes water. Vanilla's pre-hand copy
            // keeps the held item from shifting the focal distance.
            int distant = DHCompat.hasRenderingEnabled() ? getDHCompat().getDepthTex() : -1;
            sampler.auramPhoton$sampleAutomaticFocus(renderTargets.getDepthTextureNoHand().getTextureId(), distant);
        }
    }
}
