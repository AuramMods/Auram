package dev.auram.bliss.test;

import dev.auram.bliss.qa.VisualClock;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SystemTimeUniforms.Timer.class, remap = false)
abstract class ShaderClockProbe {
    @Inject(method = "getFrameTimeCounter", at = @At("HEAD"), cancellable = true)
    private void readFrozenTime(CallbackInfoReturnable<Float> callback) {
        if (VisualClock.frozen()) callback.setReturnValue(VisualClock.time());
    }
}
