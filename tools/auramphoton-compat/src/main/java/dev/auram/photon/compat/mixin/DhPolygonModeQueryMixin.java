package dev.auram.photon.compat.mixin;

import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import java.nio.IntBuffer;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.render.glObject.GLState", remap = false)
abstract class DhPolygonModeQueryMixin {
    @Redirect(method = "saveState", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL32;glGetInteger(I)I"),
            slice = @Slice(from = @At(value = "CONSTANT", args = "intValue=" + GL32.GL_POLYGON_MODE)), allow = 1)
    private int auramPhoton$readPolygonMode(int parameter) {
        // The query always writes the front and back modes. DH restores both faces together.
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer modes = stack.mallocInt(2);
            GL32.glGetIntegerv(parameter, modes);
            return modes.get(0);
        }
    }
}
