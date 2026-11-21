package dev.auram.photon.compat.mixin;

import com.seibel.distanthorizons.core.render.glObject.texture.DHDepthTexture;
import com.seibel.distanthorizons.core.render.glObject.texture.DhColorTexture;
import org.lwjgl.opengl.GL11C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.render.renderer.LodRenderer", remap = false)
abstract class DhRenderTargetResizeMixin {
    @Shadow private DHDepthTexture depthTexture;
    @Shadow private DhColorTexture nullableColorTexture;
    @Shadow private int textureWidth;
    @Shadow private int textureHeight;

    @Unique private DHDepthTexture auramPhoton$previousDepth;
    @Unique private DhColorTexture auramPhoton$previousColor;

    @Inject(method = "createAndBindTextures", at = @At("HEAD"))
    private void auramPhoton$rememberRetiredTextures(CallbackInfo callback) {
        auramPhoton$previousDepth = depthTexture;
        auramPhoton$previousColor = nullableColorTexture;
    }

    @Inject(method = "createAndBindTextures", at = @At("RETURN"))
    private void auramPhoton$retireReplacedTextures(CallbackInfo callback) {
        // Bind and announce the replacements before retiring the original textures.
        if (auramPhoton$previousDepth != null && auramPhoton$previousDepth != depthTexture) {
            auramPhoton$previousDepth.destroy();
        }
        if (auramPhoton$previousColor != null && auramPhoton$previousColor != nullableColorTexture) {
            auramPhoton$previousColor.destroy();
        }
        auramPhoton$previousDepth = null;
        auramPhoton$previousColor = null;
    }

    @Inject(method = "setGLState", at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/render/renderer/LodRenderer;createAndBindTextures()V",
            shift = At.Shift.AFTER))
    private void auramPhoton$useResizedViewport(CallbackInfo callback) {
        // DH sets the viewport before it detects the new framebuffer size.
        GL11C.glViewport(0, 0, textureWidth, textureHeight);
    }
}
