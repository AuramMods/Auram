package dev.auram.bliss.test;

import dev.auram.bliss.qa.VisualClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "art.arcane.auram.rf.client.RfFieldTexture", remap = false)
abstract class RfClockProbe {
    @Inject(method = "uploadClocks", at = @At("HEAD"), cancellable = true)
    private static void holdUploadedPhases(CallbackInfo callback) {
        if (VisualClock.frozen()) callback.cancel();
    }
}
