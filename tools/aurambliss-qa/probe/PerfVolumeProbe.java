package dev.auram.bliss.test;

import dev.auram.bliss.qa.PerfMetrics;
import dev.auram.bliss.qa.PassMetrics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.auram.photon.compat.lpv.RasterLightVolume", remap = false)
abstract class PerfVolumeProbe {
    @Unique private PassMetrics.Span auramOccupancySpan;

    @Inject(method = {"begin", "replay", "replayEntities"}, at = @At("HEAD"))
    private void beginOccupancy(CallbackInfo callback) {
        auramOccupancySpan = PassMetrics.begin("stage/lpv-occupancy");
    }

    @Inject(method = {"begin", "replay", "replayEntities"}, at = @At("RETURN"))
    private void endOccupancy(CallbackInfo callback) {
        PassMetrics.end(auramOccupancySpan);
    }

    @Inject(method = "propagate", at = @At("HEAD"))
    private void timeVolumeStart(CallbackInfo callback) {
        PerfMetrics.beginVolume();
    }

    @Inject(method = "propagate", at = @At("RETURN"))
    private void timeVolumeEnd(CallbackInfo callback) {
        PerfMetrics.endVolume();
    }
}
