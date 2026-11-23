package dev.auram.bliss.test;

import dev.auram.bliss.qa.PassMetrics;
import net.irisshaders.iris.gl.program.ComputeProgram;
import net.irisshaders.iris.gl.program.Program;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {Program.class, ComputeProgram.class}, remap = false)
abstract class PerfProgramProbe {
    @Shadow(remap = false) public abstract int getProgramId();
    @Unique private PassMetrics.Span auramComputeSpan;

    @Inject(method = "use", at = @At("RETURN"))
    private void recordProgram(CallbackInfo callback) {
        PassMetrics.useProgram(getProgramId());
    }

    // Only ComputeProgram has dispatch.
    @Inject(method = "dispatch", at = @At("HEAD"), require = 0)
    private void beginCompute(CallbackInfo callback) {
        auramComputeSpan = PassMetrics.beginProgram("compute");
    }

    @Inject(method = "dispatch", at = @At("RETURN"), require = 0)
    private void endCompute(CallbackInfo callback) {
        PassMetrics.end(auramComputeSpan);
    }
}
