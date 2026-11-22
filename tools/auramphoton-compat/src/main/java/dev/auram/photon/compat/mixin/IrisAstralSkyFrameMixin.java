package dev.auram.photon.compat.mixin;

import dev.auram.photon.compat.AstralConstellationPass;
import dev.auram.photon.compat.AstralEclipseState;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
abstract class IrisAstralSkyFrameMixin {
    @Inject(method = "beginLevelRendering", at = @At("HEAD"))
    private void auramPhoton$clearAstralState(CallbackInfo ci) {
        AstralEclipseState.clear();
        AstralConstellationPass.leave();
    }
}
