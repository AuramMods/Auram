package dev.auram.bliss.test;

import dev.auram.bliss.qa.VisualClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.progwml6.ironchest.client.render.IronChestRenderer", remap = false)
abstract class ChestClockProbe {
    @Redirect(method = "m_6922_", at = @At(value = "INVOKE", target = "Ljava/lang/System;currentTimeMillis()J"))
    private long chestClock() {
        return VisualClock.frozen() ? Math.round(VisualClock.time() * 1_000.0) : System.currentTimeMillis();
    }
}
