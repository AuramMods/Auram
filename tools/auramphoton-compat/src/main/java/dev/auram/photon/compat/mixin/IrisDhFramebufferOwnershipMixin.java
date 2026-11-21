package dev.auram.photon.compat.mixin;

import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_STENCIL_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_TEXTURE_2D;

@Mixin(value = RenderTargets.class, remap = false)
abstract class IrisDhFramebufferOwnershipMixin {
    @Unique
    private final Set<GlFramebuffer> auramPhoton$dhFramebuffers =
            Collections.newSetFromMap(new IdentityHashMap<>());

    @Inject(method = "createDHFramebuffer", at = @At("RETURN"))
    private void auramPhoton$rememberDistantHorizonsFramebuffer(CallbackInfoReturnable<GlFramebuffer> callback) {
        GlFramebuffer framebuffer = callback.getReturnValue();
        auramPhoton$dhFramebuffers.add(framebuffer);
        if (framebuffer.hasDepthAttachment()) {
            // Oculus initializes depth-only targets with Minecraft's depth buffer. DH later
            // supplies its own depth texture, which has no stencil component.
            IrisRenderSystem.framebufferTexture2D(
                    framebuffer.getId(), GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_TEXTURE_2D, 0, 0);
        }
    }

    @Redirect(method = "resizeIfNeeded", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/gl/framebuffer/GlFramebuffer;addDepthAttachment(I)V"))
    private void auramPhoton$reattachMinecraftDepth(GlFramebuffer framebuffer, int depthTexture) {
        // DHCompatInternal reconnects these framebuffers to DH's own depth texture.
        // Attaching Minecraft's packed depth-stencil texture here leaves a stale
        // stencil plane after DH replaces just the depth attachment.
        if (!auramPhoton$dhFramebuffers.contains(framebuffer)) {
            framebuffer.addDepthAttachment(depthTexture);
        }
    }
}
