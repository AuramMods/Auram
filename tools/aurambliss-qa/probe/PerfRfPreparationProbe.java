package dev.auram.bliss.test;

import dev.auram.bliss.qa.PassMetrics;
import dev.auram.bliss.qa.RfPreparedReadback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.auram.photon.compat.rf.PreparedRfStreaks", remap = false)
abstract class PerfRfPreparationProbe {
    @Unique private PassMetrics.Span auramRfSpan;

    @Inject(method = "prepareFrame", at = @At("HEAD"))
    private void startRfPreparation(CallbackInfo callback) {
        auramRfSpan = PassMetrics.begin("stage/rf-preparation");
    }

    @Inject(method = "prepareFrame", at = @At("RETURN"))
    private void endRfPreparation(CallbackInfo callback) {
        PassMetrics.end(auramRfSpan);
        RfPreparedReadback.prepared();
    }
}
