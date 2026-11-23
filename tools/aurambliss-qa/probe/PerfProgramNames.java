package dev.auram.bliss.test;

import dev.auram.bliss.qa.PassMetrics;
import net.irisshaders.iris.gl.shader.GlShader;
import net.irisshaders.iris.gl.shader.ProgramCreator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ProgramCreator.class, remap = false)
abstract class PerfProgramNames {
    @Inject(method = "create", at = @At("RETURN"))
    private static void rememberName(String name, GlShader[] shaders, CallbackInfoReturnable<Integer> callback) {
        PassMetrics.rememberProgram(callback.getReturnValueI(), name);
    }
}
