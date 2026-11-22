package dev.auram.photon.compat.mixin;

import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.auram.photon.compat.lpv.RasterLightVolume;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = VertexBuffer.class, remap = false)
abstract class BlissShadowEntityDrawMixin {
    @Shadow private VertexFormat f_85917_;
    @Shadow private int f_166863_;
    @Shadow
    private VertexFormat.IndexType m_231231_() {
        throw new AssertionError();
    }

    @Redirect(method = "m_166876_", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;m_166882_()V"))
    private void auramBliss$captureShadowEntities(VertexBuffer buffer, Matrix4f modelView, Matrix4f projection,
            ShaderInstance shader) {
        buffer.m_166882_();
        if (!RasterLightVolume.isCapturingBlissEntities() || f_166863_ <= 0) return;
        var elements = f_85917_.m_86023_();
        int entityIndex = elements.indexOf(IrisVertexFormats.ENTITY_ID_ELEMENT);
        if (entityIndex < 0) return;
        for (int index = 0; index < elements.size(); index++) {
            if (elements.get(index).m_86048_() == VertexFormatElement.Usage.POSITION) {
                RasterLightVolume.replayBlissEntities(f_166863_, m_231231_().f_166923_, index, entityIndex, modelView);
                return;
            }
        }
    }
}
