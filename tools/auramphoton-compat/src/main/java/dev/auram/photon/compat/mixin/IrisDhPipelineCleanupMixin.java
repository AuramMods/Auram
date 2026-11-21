package dev.auram.photon.compat.mixin;

import net.irisshaders.iris.compat.dh.DHCompatInternal;
import net.irisshaders.iris.compat.dh.IrisGenericRenderProgram;
import net.irisshaders.iris.targets.DepthTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DHCompatInternal.class, remap = false)
abstract class IrisDhPipelineCleanupMixin {
    @Shadow private IrisGenericRenderProgram genericShader;
    @Shadow private DepthTexture depthTexNoTranslucent;

    @Inject(method = "clear", at = @At("RETURN"))
    private void auramPhoton$releaseDistantHorizonsResources(CallbackInfo callback) {
        // Oculus unbinds the generic shader from DH before it returns.
        if (genericShader != null) {
            genericShader.free();
            genericShader = null;
        }
        if (depthTexNoTranslucent != null) {
            depthTexNoTranslucent.destroy();
            depthTexNoTranslucent = null;
        }
    }
}
