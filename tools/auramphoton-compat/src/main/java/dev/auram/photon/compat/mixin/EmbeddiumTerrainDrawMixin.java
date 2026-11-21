package dev.auram.photon.compat.mixin;

import dev.auram.photon.compat.lpv.RasterLightVolume;
import org.lwjgl.opengl.GL32C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "me.jellysquid.mods.sodium.client.gl.device.GLRenderDevice$ImmediateDrawCommandList", remap = false)
abstract class EmbeddiumTerrainDrawMixin {
    @Redirect(method = "multiDrawElementsBaseVertex", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL32C;nglMultiDrawElementsBaseVertex(IJIJIJ)V"))
    private void auramPhoton$captureShadowTerrain(int mode, long counts, int type, long indices, int draws, long baseVertices) {
        GL32C.nglMultiDrawElementsBaseVertex(mode, counts, type, indices, draws, baseVertices);
        RasterLightVolume.replayTerrain(counts, type, indices, draws, baseVertices);
    }
}
