package dev.auram.photon.compat.mixin;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "hellfirepvp.astralsorcery.client.sky.AstralSkyRenderer", remap = false)
abstract class AstralSkyDiskPhaseMixin {
    @Unique private WorldRenderingPipeline auramPhoton$diskPipeline;

    @Inject(method = "renderSun", at = @At("HEAD"))
    private void auramPhoton$beginSun(CallbackInfo ci) {
        auramPhoton$beginDisk(WorldRenderingPhase.SUN);
    }

    @Inject(method = "renderMoon", at = @At("HEAD"))
    private void auramPhoton$beginMoon(CallbackInfo ci) {
        auramPhoton$beginDisk(WorldRenderingPhase.MOON);
    }

    @Inject(method = {"renderSun", "renderMoon"}, at = @At("RETURN"))
    private void auramPhoton$endDisk(CallbackInfo ci) {
        if (auramPhoton$diskPipeline != null) {
            auramPhoton$diskPipeline.setPhase(WorldRenderingPhase.CUSTOM_SKY);
            auramPhoton$diskPipeline = null;
        }
    }

    @Unique
    private void auramPhoton$beginDisk(WorldRenderingPhase diskPhase) {
        String packName = Iris.getCurrentPackName();
        if (!"AuramPhoton".equals(packName) && !"AuramPhoton.zip".equals(packName)) {
            return;
        }

        // Astral replaces LevelRenderer.renderSky, including Oculus's phase changes inside it.
        // Its shadow pass draws the same disks outside CUSTOM_SKY.
        var pipeline = Iris.getPipelineManager().getPipeline().orElse(null);
        if (pipeline != null && pipeline.getPhase() == WorldRenderingPhase.CUSTOM_SKY) {
            pipeline.setPhase(diskPhase);
            auramPhoton$diskPipeline = pipeline;
        }
    }
}
