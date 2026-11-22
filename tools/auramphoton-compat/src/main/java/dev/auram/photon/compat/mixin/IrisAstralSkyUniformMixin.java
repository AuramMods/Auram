package dev.auram.photon.compat.mixin;

import dev.auram.photon.compat.AstralConstellationPass;
import dev.auram.photon.compat.AstralEclipseState;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.uniform.DynamicUniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.uniforms.CommonUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CommonUniforms.class, remap = false)
abstract class IrisAstralSkyUniformMixin {
    @Inject(method = "addDynamicUniforms", at = @At("TAIL"))
    private static void auramPhoton$addAstralUniforms(DynamicUniformHolder uniforms, FogMode mode, CallbackInfo ci) {
        uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "auramAstralSolarProgress", AstralEclipseState::solarProgress);
        uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "auramAstralLunarCoverage", AstralEclipseState::lunarCoverage);
        // The scope changes between draws. Oculus updates dynamic uniforms on each shader application.
        uniforms.uniform1i("auramAstralConstellation", AstralConstellationPass::uniformValue, listener -> {});
    }
}
