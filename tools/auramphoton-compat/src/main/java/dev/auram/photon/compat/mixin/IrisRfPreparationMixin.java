package dev.auram.photon.compat.mixin;

import dev.auram.photon.compat.rf.PreparedRfStreaks;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
abstract class IrisRfPreparationMixin {
    @Unique private PreparedRfStreaks auramBliss$rf;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void auramBliss$createRfPreparation(ProgramSet programs, CallbackInfo callback) {
        auramBliss$rf = PreparedRfStreaks.create((IrisRenderingPipeline) (Object) this, programs);
    }

    @Inject(method = "beginLevelRendering", at = @At("TAIL"))
    private void auramBliss$prepareRf(CallbackInfo callback) {
        if (auramBliss$rf != null) auramBliss$rf.prepareFrame();
    }

    @Inject(method = "finalizeLevelRendering", at = @At("TAIL"))
    private void auramBliss$finishRf(CallbackInfo callback) {
        if (auramBliss$rf != null) auramBliss$rf.finishFrame();
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void auramBliss$destroyRf(CallbackInfo callback) {
        if (auramBliss$rf != null) auramBliss$rf.close();
    }
}
