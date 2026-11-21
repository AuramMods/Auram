package dev.auram.photon.compat.mixin;

import dev.auram.photon.compat.DhBoxShaderSource;
import org.lwjgl.system.Platform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.render.openGl.glObject.shader.GlShader", remap = false)
abstract class Dh333GenericShaderSourceMixin {
    @Inject(method = "loadFile", at = @At("RETURN"), cancellable = true)
    private static void auramPhoton$useProceduralBoxPositions(String path, boolean external,
            CallbackInfoReturnable<String> callback) {
        if (external || Platform.get() != Platform.MACOSX) {
            return;
        }
        boolean direct = path.equals("assets/distanthorizons/shaders/generic/gl/direct/vert.vert");
        if (!direct && !path.equals("assets/distanthorizons/shaders/generic/gl/instanced/vert.vert")) {
            return;
        }

        int[] indices = direct ? Dh333GenericGeometryAccess.auramPhoton$getBoxIndices() : null;
        float[] positions = Dh333GenericGeometryAccess.auramPhoton$getBoxVertices();
        String source = DhBoxShaderSource.useVertexId(callback.getReturnValue(), positions, indices);
        callback.setReturnValue(source);
    }
}
