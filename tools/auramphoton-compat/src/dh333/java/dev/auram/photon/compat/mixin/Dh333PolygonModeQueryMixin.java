package dev.auram.photon.compat.mixin;

import com.seibel.distanthorizons.lwjgl.ILWJGLService;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import java.nio.IntBuffer;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.render.openGl.glObject.GLState", remap = false)
abstract class Dh333PolygonModeQueryMixin {
    @Redirect(method = "saveState", at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/lwjgl/ILWJGLService;glGetInteger(I)I"),
            slice = @Slice(from = @At(value = "CONSTANT", args = "intValue=" + GL32.GL_POLYGON_MODE)), allow = 1)
    private int auramPhoton$readPolygonMode(ILWJGLService service, int parameter) {
        // The query writes the modes of both faces. DH restores the same mode to both.
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer modes = stack.mallocInt(2);
            GL32.glGetIntegerv(parameter, modes);
            return modes.get(0);
        }
    }
}
