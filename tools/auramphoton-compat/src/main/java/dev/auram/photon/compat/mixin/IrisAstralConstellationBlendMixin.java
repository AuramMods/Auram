package dev.auram.photon.compat.mixin;

import dev.auram.photon.compat.AstralConstellationPass;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ExtendedShader", remap = false)
abstract class IrisAstralConstellationBlendMixin {
    @Redirect(method = "m_173363_()V", at = @At(value = "INVOKE", target =
            "Lnet/irisshaders/iris/gl/blending/BlendModeOverride;apply()V"))
    private void auramPhoton$preserveConstellationBlend(BlendModeOverride override) {
        if (!AstralConstellationPass.active()) {
            override.apply();
        }
    }
}
