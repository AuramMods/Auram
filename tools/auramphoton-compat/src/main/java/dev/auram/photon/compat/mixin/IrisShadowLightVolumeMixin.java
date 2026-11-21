package dev.auram.photon.compat.mixin;

import dev.auram.photon.compat.lpv.RasterLightVolume;
import net.irisshaders.iris.shadows.ShadowRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ShadowRenderer.class, remap = false)
abstract class IrisShadowLightVolumeMixin {
    @Inject(method = "renderShadows", at = @At(value = "FIELD",
            target = "Lnet/irisshaders/iris/shadows/ShadowRenderer;MODELVIEW:Lorg/joml/Matrix4f;",
            opcode = Opcodes.PUTSTATIC, shift = At.Shift.AFTER))
    private void auramPhoton$beginVoxelization(CallbackInfo callback) {
        RasterLightVolume.beginShadowCapture();
    }

    @Inject(method = "renderShadows", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/shadows/ShadowCompositeRenderer;renderAll()V"))
    private void auramPhoton$propagateLights(CallbackInfo callback) {
        RasterLightVolume.finishShadowCapture();
    }
}
