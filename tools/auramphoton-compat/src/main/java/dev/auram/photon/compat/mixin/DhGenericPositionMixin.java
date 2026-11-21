package dev.auram.photon.compat.mixin;

import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiGenericObjectShaderProgram;
import com.seibel.distanthorizons.core.render.glObject.buffer.GLElementBuffer;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.system.Platform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.render.renderer.generic.GenericObjectRenderer", remap = false)
abstract class DhGenericPositionMixin {
    @Shadow private IDhApiGenericObjectShaderProgram directShaderProgram;
    @Shadow private IDhApiGenericObjectShaderProgram instancedShaderProgram;

    @Unique private boolean auramPhoton$proceduralDirect;

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
            target = "Lcom/seibel/distanthorizons/core/render/glObject/buffer/GLElementBuffer;bind()V"))
    private void auramPhoton$bindBoxIndices(GLElementBuffer buffer) {
        if (auramPhoton$proceduralDirect) {
            GL32C.glBindBuffer(GL32C.GL_ELEMENT_ARRAY_BUFFER, 0);
        } else {
            buffer.bind();
        }
    }

    @Redirect(method = "renderBox", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL32;glDrawElements(IIIJ)V"))
    private void auramPhoton$drawBox(int mode, int count, int type, long offset) {
        if (auramPhoton$proceduralDirect) {
            GL32C.glDrawArrays(mode, 0, count);
        } else {
            GL32C.glDrawElements(mode, count, type, offset);
        }
    }
}
