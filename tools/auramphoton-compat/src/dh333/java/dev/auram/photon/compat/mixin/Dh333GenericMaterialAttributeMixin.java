package dev.auram.photon.compat.mixin;

import com.seibel.distanthorizons.lwjgl.ILWJGLService;
import org.lwjgl.opengl.GL32C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.render.openGl.generic.GlGenericObjectRenderer", remap = false)
abstract class Dh333GenericMaterialAttributeMixin {
    @Redirect(method = "renderBoxGroupInstanced", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/seibel/distanthorizons/lwjgl/ILWJGLService;glVertexAttribIPointer(IIIIJ)V"))
    private void auramPhoton$readMaterialInts(ILWJGLService service, int index, int size, int type, int stride, long offset) {
        // RenderableBoxGroup uploads one Java int per instance.
        service.glVertexAttribIPointer(index, size, GL32C.GL_INT, Integer.BYTES, offset);
    }
}
