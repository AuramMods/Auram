package dev.auram.photon.compat.mixin;

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

        String vertexId = "gl_VertexID";
        String source = output.toString();
        StringBuilder geometry = new StringBuilder();
        if (direct) {
            // The native direct draw submits triangles in the original index order.
            // Directional shading still uses the original vertex ID of each face.
            int[] indices = DhGenericGeometryAccess.auramPhoton$getBoxIndices();
            geometry.append("const int auramBoxIndices[").append(indices.length).append("] = int[](");
            for (int index : indices) {
                geometry.append(index).append(", ");
            }
            geometry.setLength(geometry.length() - 2);
            geometry.append(");\n#define auramBoxVertex auramBoxIndices[gl_VertexID]\n");
            source = source.replace("gl_VertexID", "auramBoxVertex");
            vertexId = "auramBoxVertex";
        }

        float[] positions = DhGenericGeometryAccess.auramPhoton$getBoxVertices();
        geometry.append("const vec3 auramBoxPositions[").append(positions.length / 3).append("] = vec3[](\n");
        for (int offset = 0; offset < positions.length; offset += 3) {
            geometry.append("    vec3(").append(positions[offset]).append(", ")
                    .append(positions[offset + 1]).append(", ").append(positions[offset + 2]).append("),\n");
        }
        geometry.setLength(geometry.length() - 2);
        geometry.append("\n);\n#define vPosition auramBoxPositions[").append(vertexId).append("]");
        String declaration = "in vec3 vPosition;";
        int start = source.indexOf(declaration);
        output.replace(0, output.length(), source.substring(0, start) + geometry
                + source.substring(start + declaration.length()));
    }
}
