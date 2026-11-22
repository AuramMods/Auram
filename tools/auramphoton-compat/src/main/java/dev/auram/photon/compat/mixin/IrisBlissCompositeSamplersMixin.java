package dev.auram.photon.compat.mixin;

import java.util.function.IntSupplier;
import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.gl.texture.TextureType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.irisshaders.iris.gl.program.ProgramSamplers$Builder", remap = false)
abstract class IrisBlissCompositeSamplersMixin {
    @Shadow public abstract boolean hasSampler(String name);

    @Inject(method = "addDefaultSampler", at = @At("HEAD"), cancellable = true)
    private void auramBliss$skipUnusedDefaultTarget(TextureType type, IntSupplier texture,
            ValueUpdateNotifier notifier, GlSampler sampler, String[] names,
            CallbackInfoReturnable<Boolean> callback) {
        if (names.length == 2 && "colortex0".equals(names[0]) && hasSampler("auram_bliss_light")
                && !hasSampler(names[0]) && !hasSampler(names[1])) {
            // Oculus assigns a default even when the linked Bliss program has no default target.
            callback.setReturnValue(false);
        }
    }
}
