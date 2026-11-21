package dev.auram.photon.compat.mixin;

import dev.auram.photon.compat.lpv.RasterLightVolume;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
abstract class IrisPipelineLightVolumeMixin {
    @Unique private RasterLightVolume auramPhoton$volume;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void auramPhoton$createLightVolume(ProgramSet set, CallbackInfo callback) {
        auramPhoton$volume = RasterLightVolume.create((IrisRenderingPipeline) (Object) this, set);
    }

    @Inject(method = "beginLevelRendering", at = @At("HEAD"))
    private void auramPhoton$selectLightVolume(CallbackInfo callback) {
        RasterLightVolume.select(auramPhoton$volume);
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void auramPhoton$destroyLightVolume(CallbackInfo callback) {
        if (auramPhoton$volume != null) {
            auramPhoton$volume.close();
            auramPhoton$volume = null;
        }
    }
}
