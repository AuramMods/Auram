package dev.auram.photon.compat.mixin;

import com.google.common.collect.ImmutableSet;
import dev.auram.photon.compat.ExposureMeter;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(value = CompositeRenderer.class, remap = false)
abstract class IrisExposureMeterMixin {
    @Shadow @Final private RenderTargets renderTargets;

    @Unique private ExposureMeter auramPhoton$meter;
    @Unique private Program auramPhoton$exposureProgram;
    @Unique private boolean auramPhoton$sceneFromAlt;

    @Redirect(method = "createProgram", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/gl/program/ProgramBuilder;build()Lnet/irisshaders/iris/gl/program/Program;"))
    private Program auramPhoton$bindMeter(ProgramBuilder builder, ProgramSource source,
                                         ImmutableSet<Integer> flipped, ImmutableSet<Integer> previouslyFlipped,
                                         Supplier<ShadowRenderTargets> shadows) {
        if (!builder.hasSampler("auram_exposure_meter")) return builder.build();

        var options = Iris.getCurrentPack().orElseThrow().getShaderPackOptions().getOptionValues();
        ExposureMeter meter = new ExposureMeter(Float.parseFloat(options.getStringValueOrDefault("TAAU_RENDER_SCALE")));
        builder.addDynamicSampler(meter::texture, "auram_exposure_meter");
        auramPhoton$meter = meter;
        auramPhoton$sceneFromAlt = flipped.contains(0);
        return auramPhoton$exposureProgram = builder.build();
    }

    @Redirect(method = "renderAll", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/gl/program/Program;use()V"))
    private void auramPhoton$measureScene(Program program) {
        if (program == auramPhoton$exposureProgram) {
            auramPhoton$meter.update(renderTargets.get(0), auramPhoton$sceneFromAlt);
        }
        program.use();
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void auramPhoton$releaseMeter(CallbackInfo callback) {
        if (auramPhoton$meter != null) auramPhoton$meter.close();
    }
}
