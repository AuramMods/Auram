package dev.auram.photon.compat.mixin;

import dev.auram.photon.compat.WarpMistState;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.uniform.DynamicUniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.uniforms.CommonUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CommonUniforms.class, remap = false)
abstract class IrisWarpMistUniformMixin {
    @Inject(method = "addDynamicUniforms", at = @At("TAIL"))
    private static void auramPhoton$addWarpMistUniform(DynamicUniformHolder uniforms, FogMode mode, CallbackInfo callback) {
        if (WarpMistState.available()) {
            uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "auramWarpMist", WarpMistState::intensity);
            uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "auramBlissNearWarpMist", WarpMistState::visibleIntensity);
        }
    }
}
