package dev.auram.bliss.test;

import dev.auram.bliss.qa.RfFixture;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerLevel.class, remap = false)
abstract class RfFixtureTick {
    @Inject(method = "m_8793_", at = @At("HEAD"))
    private void reportRf(BooleanSupplier haveTime, CallbackInfo callback) {
        RfFixture.tick((ServerLevel) (Object) this);
    }
}
