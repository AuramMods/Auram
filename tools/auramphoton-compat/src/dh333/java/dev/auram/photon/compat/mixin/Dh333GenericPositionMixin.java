package dev.auram.photon.compat.mixin;

import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiGenericObjectShaderProgram;
import com.seibel.distanthorizons.common.render.openGl.glObject.buffer.GLIndexBuffer;
import com.seibel.distanthorizons.core.jar.EPlatform;
import com.seibel.distanthorizons.lwjgl.ILWJGLService;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.system.Platform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.render.openGl.generic.GlGenericObjectRenderer", remap = false)
abstract class Dh333GenericPositionMixin {
    @Shadow private IDhApiGenericObjectShaderProgram directShaderProgram;
    @Shadow private IDhApiGenericObjectShaderProgram instancedShaderProgram;

    @Unique private boolean auramPhoton$proceduralDirect;

    @Redirect(method = {"init", "render"}, at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/jar/EPlatform;get()Lcom/seibel/distanthorizons/core/jar/EPlatform;"))
    private EPlatform auramPhoton$allowPatchedMacRendering() {
        EPlatform platform = EPlatform.get();
        // These two DH checks only decide whether to shut off its generic renderer on macOS.
        return platform == EPlatform.MACOS ? EPlatform.LINUX : platform;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/api/interfaces/override/rendering/IDhApiGenericObjectShaderProgram;bindVertexBuffer(I)V"))
    private void auramPhoton$bindBoxPosition(IDhApiGenericObjectShaderProgram program, int buffer) {
        boolean mac = Platform.get() == Platform.MACOSX;
        auramPhoton$proceduralDirect = mac && program == directShaderProgram;
        if (auramPhoton$proceduralDirect || (mac && program == instancedShaderProgram)) {
            // GLEngine's immediate draw path can read a null host address for this
            // position stream. The two built-in shaders use the same cube by vertex ID.
            GL32C.glDisableVertexAttribArray(0);
        } else {
            program.bindVertexBuffer(buffer);
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/common/render/openGl/glObject/buffer/GLIndexBuffer;bind()V"))
    private void auramPhoton$bindBoxIndices(GLIndexBuffer buffer) {
        if (auramPhoton$proceduralDirect) {
            GL32C.glBindBuffer(GL32C.GL_ELEMENT_ARRAY_BUFFER, 0);
        } else {
            buffer.bind();
        }
    }

    @Redirect(method = "renderBoxGroupDirect", at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/lwjgl/ILWJGLService;glDrawElements(IIII)V"))
    private void auramPhoton$drawBox(ILWJGLService service, int mode, int count, int type, int offset) {
        if (auramPhoton$proceduralDirect) {
            GL32C.glDrawArrays(mode, 0, count);
        } else {
            service.glDrawElements(mode, count, type, offset);
        }
    }
}
