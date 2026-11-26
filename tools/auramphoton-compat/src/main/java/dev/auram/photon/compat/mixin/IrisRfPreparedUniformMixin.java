package dev.auram.photon.compat.mixin;

import dev.auram.photon.compat.rf.PreparedRfStreaks;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.uniform.DynamicUniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.uniforms.CommonUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CommonUniforms.class, remap = false)
abstract class IrisRfPreparedUniformMixin {
    @Inject(method = "addDynamicUniforms", at = @At("TAIL"))
    private static void auramBliss$addPreparedUniform(DynamicUniformHolder uniforms, FogMode mode, CallbackInfo callback) {
        uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "auramRfPrepared", PreparedRfStreaks::isPrepared);
    }
}
