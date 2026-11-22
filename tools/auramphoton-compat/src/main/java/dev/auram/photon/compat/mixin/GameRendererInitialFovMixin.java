package dev.auram.photon.compat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.GameRenderer", remap = false)
abstract class GameRendererInitialFovMixin {
    @Shadow
    private float f_109066_; // fov

    @Shadow
    private float f_109067_; // oldFov

    @Inject(method = "<init>", at = @At("RETURN"))
    private void auramPhoton$initializeFov(CallbackInfo callback) {
        // A world can render before the first FOV tick. Start interpolation at
        // the neutral multiplier so the configured FOV yields a valid projection.
        if (f_109066_ == 0.0f && f_109067_ == 0.0f) {
            f_109066_ = 1.0f;
            f_109067_ = 1.0f;
        }
    }
}
