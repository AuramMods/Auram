package dev.auram.photon.compat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.render.renderer.generic.GenericObjectRenderer", remap = false)
public interface DhGenericGeometryAccess {
    @Accessor("BOX_VERTICES")
    static float[] auramPhoton$getBoxVertices() {
        throw new AssertionError();
    }

    @Accessor("BOX_INDICES")
    static int[] auramPhoton$getBoxIndices() {
        throw new AssertionError();
    }
}
