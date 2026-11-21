package dev.auram.photon.compat.mixin;

import dev.auram.photon.compat.lpv.RasterLightVolume;
import net.irisshaders.iris.compat.sodium.impl.shader_overrides.IrisChunkShaderInterface;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisChunkShaderInterface.class, remap = false)
abstract class IrisTerrainUniformsMixin {
    @Inject(method = "setModelViewMatrix", at = @At("TAIL"))
    private void auramPhoton$modelView(Matrix4fc matrix, CallbackInfo callback) {
        RasterLightVolume.modelView(matrix);
    }

    @Inject(method = "setRegionOffset", at = @At("TAIL"))
    private void auramPhoton$regionOffset(float x, float y, float z, CallbackInfo callback) {
        RasterLightVolume.regionOffset(x, y, z);
    }
}
