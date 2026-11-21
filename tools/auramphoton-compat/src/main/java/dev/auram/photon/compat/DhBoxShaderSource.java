package dev.auram.photon.compat;

public final class DhBoxShaderSource {
    private DhBoxShaderSource() {}

    public static String useVertexId(String source, float[] positions, int[] indices) {
        String vertexId = "gl_VertexID";
        StringBuilder geometry = new StringBuilder();
        if (indices != null) {
            // The native direct draw submits triangles in the original index order.
            // Directional shading still uses the original vertex ID of each face.
            geometry.append("const int auramBoxIndices[").append(indices.length).append("] = int[](");
            for (int index : indices) {
                geometry.append(index).append(", ");
            }
            geometry.setLength(geometry.length() - 2);
            geometry.append(");\n#define auramBoxVertex auramBoxIndices[gl_VertexID]\n");
            source = source.replace("gl_VertexID", "auramBoxVertex");
            vertexId = "auramBoxVertex";
        }

        geometry.append("const vec3 auramBoxPositions[").append(positions.length / 3).append("] = vec3[](\n");
        for (int offset = 0; offset < positions.length; offset += 3) {
            geometry.append("    vec3(").append(positions[offset]).append(", ")
                    .append(positions[offset + 1]).append(", ").append(positions[offset + 2]).append("),\n");
        }
        geometry.setLength(geometry.length() - 2);
        geometry.append("\n);\n#define vPosition auramBoxPositions[").append(vertexId).append("]");
        String declaration = "in vec3 vPosition;";
        int start = source.indexOf(declaration);
        if (start < 0) {
            throw new IllegalStateException("Cannot find the DH generic box position input");
        }
        return source.substring(0, start) + geometry + source.substring(start + declaration.length());
    }
}
