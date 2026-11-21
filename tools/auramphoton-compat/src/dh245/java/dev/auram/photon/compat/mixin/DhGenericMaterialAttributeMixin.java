package dev.auram.photon.compat.mixin;

import org.lwjgl.opengl.GL32C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.render.renderer.generic.GenericObjectRenderer", remap = false)
abstract class DhGenericMaterialAttributeMixin {
    @Redirect(method = "renderBoxGroupInstanced", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lorg/lwjgl/opengl/GL32;glVertexAttribIPointer(IIIIJ)V"))
    private void auramPhoton$readMaterialInts(int index, int size, int type, int stride, long offset) {
        // RenderableBoxGroup uploads one Java int per instance.
        GL32C.glVertexAttribIPointer(index, size, GL32C.GL_INT, Integer.BYTES, offset);
    }
}
