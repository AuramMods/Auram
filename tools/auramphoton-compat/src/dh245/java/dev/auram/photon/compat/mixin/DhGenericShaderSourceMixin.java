package dev.auram.photon.compat.mixin;

import dev.auram.photon.compat.DhBoxShaderSource;
import org.lwjgl.system.Platform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.render.glObject.shader.Shader", remap = false)
abstract class DhGenericShaderSourceMixin {
    @Inject(method = "loadFile", at = @At("RETURN"))
    private static void auramPhoton$useProceduralBoxPositions(String path, boolean external,
            StringBuilder output, CallbackInfoReturnable<StringBuilder> callback) {
        if (external || Platform.get() != Platform.MACOSX) {
            return;
        }
        boolean direct = path.equals("shaders/genericObject/direct/vert.vert");
        if (!direct && !path.equals("shaders/genericObject/instanced/vert.vert")) {
            return;
        }

        int[] indices = direct ? DhGenericGeometryAccess.auramPhoton$getBoxIndices() : null;
        float[] positions = DhGenericGeometryAccess.auramPhoton$getBoxVertices();
        String source = DhBoxShaderSource.useVertexId(output.toString(), positions, indices);
        output.replace(0, output.length(), source);
    }
}
