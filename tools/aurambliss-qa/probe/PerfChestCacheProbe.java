package dev.auram.bliss.test;

import dev.auram.bliss.qa.PassMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.auram.photon.compat.chest.CrystalChestMeshes", remap = false)
abstract class PerfChestCacheProbe {
    @Inject(method = "draw", at = @At("RETURN"))
    private void countCacheUse(CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValueZ()) PassMetrics.cachedItem();
    }
}
