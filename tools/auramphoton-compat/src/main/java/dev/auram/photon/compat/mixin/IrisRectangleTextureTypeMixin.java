package dev.auram.photon.compat.mixin;

import net.irisshaders.iris.gl.texture.TextureType;
import org.lwjgl.opengl.GL31C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TextureType.class, remap = false)
abstract class IrisRectangleTextureTypeMixin {
    @Inject(method = "getGlType", at = @At("HEAD"), cancellable = true)
    private void auramPhoton$rectangleTarget(CallbackInfoReturnable<Integer> callback) {
        if ((Object) this == TextureType.TEXTURE_RECTANGLE) {
            callback.setReturnValue(GL31C.GL_TEXTURE_RECTANGLE);
        }
    }
}
