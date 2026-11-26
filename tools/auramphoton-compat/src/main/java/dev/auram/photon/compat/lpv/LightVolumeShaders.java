package dev.auram.photon.compat.lpv;

import static net.irisshaders.iris.compat.sodium.impl.IrisChunkShaderBindingPoints.*;
import static org.lwjgl.opengl.GL41C.*;
import static org.lwjgl.opengl.GL43C.GL_COMPUTE_SHADER;

final class LightVolumeShaders {
    static final String VERSION = "#version 410 core\n";

    private static final String FULLSCREEN_VERTEX = VERSION + """
            flat out int slice;
            void main() {
                vec2 corners[3] = vec2[3](vec2(-1, -1), vec2(3, -1), vec2(-1, 3));
                gl_Position = vec4(corners[gl_VertexID], 0, 1);
                slice = gl_InstanceID;
            }
            """;

    private static final String VOLUME_LAYERS = VERSION + """
            layout(triangles) in;
            layout(triangle_strip, max_vertices = 3) out;
            flat in int slice[];
            flat out int auram_photon_layer;
            void main() {
                for (int i = 0; i < 3; ++i) {
                    gl_Position = gl_in[i].gl_Position;
                    gl_Layer = slice[0];
                    auram_photon_layer = slice[0];
                    EmitVertex();
                }
                EndPrimitive();
            }
            """;

    private static final String OCCUPANCY_VERTEX = """
            flat out uint auram_photon_material;
            uniform int volume_side;
            uniform int atlas_columns;
            uniform vec2 atlas_size;

            void main() {
                auram_photon_shadow_vertex();
                ivec4 voxel = auram_photon_voxel;
                if (voxel.w == 0 || any(lessThan(voxel.xyz, ivec3(0)))
                        || any(greaterThanEqual(voxel.xyz, ivec3(volume_side)))) {
                    gl_Position = vec4(-2, -2, -2, 1);
                    return;
                }

                ivec2 tile = ivec2(voxel.z % atlas_columns, voxel.z / atlas_columns);
                vec2 pixel = vec2(voxel.xy + tile * volume_side) + 0.5;
                gl_Position = vec4(2.0 * pixel / atlas_size - 1.0, 0, 1);
                gl_PointSize = 1.0;
                auram_photon_material = uint(voxel.w);
            }
            """;

    private static final String VOXEL_MATERIAL = VERSION + """
            flat in uint auram_photon_material;
            layout(location = 0) out uint occupancy;
            void main() { occupancy = auram_photon_material; }
            """;

    private LightVolumeShaders() {}

    static int occupancy(String vertex, int side, int columns, int width, int height) {
        // Keep Oculus's attribute decoding and Photon voxel classification in the replay.
        int versionEnd = vertex.indexOf('\n') + 1;
        String voxelVertex = vertex.substring(0, versionEnd)
                + "#define main auram_photon_shadow_vertex\n"
                + vertex.substring(versionEnd) + "\n#undef main\n" + OCCUPANCY_VERTEX;
        int program = link("occupancy", voxelVertex, null, VOXEL_MATERIAL, true);
        setVoxelAtlas(program, side, columns, width, height);
        return program;
    }

    static int blissEntityOccupancy(String vertex, int position, int entity, int side, int columns, int width, int height) {
        String voxelVertex = VERSION + "#define main auram_photon_shadow_vertex\n"
                + "#define BLISS_POSITION_ATTRIBUTE " + position + "\n"
                + "#define BLISS_ENTITY_ATTRIBUTE " + entity + "\n"
                + vertex + "\n#undef main\n" + OCCUPANCY_VERTEX;
        int program = link("entity occupancy", voxelVertex, null, VOXEL_MATERIAL, false);
        setVoxelAtlas(program, side, columns, width, height);
        return program;
    }

    private static void setVoxelAtlas(int program, int side, int columns, int width, int height) {
        glProgramUniform1i(program, glGetUniformLocation(program, "volume_side"), side);
        glProgramUniform1i(program, glGetUniformLocation(program, "atlas_columns"), columns);
        glProgramUniform2f(program, glGetUniformLocation(program, "atlas_size"), width, height);
    }

    static int transport(String fragment, int columns) {
        int program = link("transport", FULLSCREEN_VERTEX, VOLUME_LAYERS, VERSION + fragment, false);
        setTransportSamplers(program, columns);
        return program;
    }

    static int computeTransport(String source, int columns) {
        int program = glCreateProgram();
        try {
            attach(program, GL_COMPUTE_SHADER, "compute transport", "#version 430 core\n" + source);
            linkProgram(program, "compute transport");
            setTransportSamplers(program, columns);
            return program;
        } catch (RuntimeException | Error failure) {
            glDeleteProgram(program);
            throw failure;
        }
    }

    private static void setTransportSamplers(int program, int columns) {
        glProgramUniform1i(program, glGetUniformLocation(program, "previous_light"), 0);
        glProgramUniform1i(program, glGetUniformLocation(program, "voxel_sampler"), 1);
        glProgramUniform1i(program, glGetUniformLocation(program, "light_data_sampler"), 2);
        glProgramUniform1i(program, glGetUniformLocation(program, "voxel_atlas_columns"), columns);
    }

    static int palette(String fragment) {
        return link("palette", FULLSCREEN_VERTEX, null, VERSION + fragment, false);
    }

    private static int link(String label, String vertex, String geometry, String fragment, boolean terrain) {
        int program = glCreateProgram();
        try {
            attach(program, GL_VERTEX_SHADER, label + " vertex", vertex);
            if (geometry != null) {
                attach(program, GL_GEOMETRY_SHADER, label + " geometry", geometry);
            }
            attach(program, GL_FRAGMENT_SHADER, label + " fragment", fragment);
            if (terrain) {
                glBindAttribLocation(program, ATTRIBUTE_POSITION_ID, "a_PosId");
                glBindAttribLocation(program, ATTRIBUTE_COLOR, "a_Color");
                glBindAttribLocation(program, ATTRIBUTE_BLOCK_TEXTURE, "a_TexCoord");
                glBindAttribLocation(program, ATTRIBUTE_LIGHT_TEXTURE, "a_LightCoord");
                glBindAttribLocation(program, NORMAL, "iris_Normal");
                glBindAttribLocation(program, BLOCK_ID, "mc_Entity");
                glBindAttribLocation(program, MID_TEX_COORD, "mc_midTexCoord");
                glBindAttribLocation(program, TANGENT, "at_tangent");
                glBindAttribLocation(program, MID_BLOCK, "at_midBlock");
            }
            linkProgram(program, label);
            return program;
        } catch (RuntimeException | Error failure) {
            glDeleteProgram(program);
            throw failure;
        }
    }

    private static void linkProgram(int program, String label) {
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) != GL_TRUE) {
            throw new IllegalStateException("AuramPhoton LPV " + label + " link failed:\n" + glGetProgramInfoLog(program));
        }
    }

    private static void attach(int program, int type, String label, String source) {
        int shader = glCreateShader(type);
        try {
            glShaderSource(shader, source);
            glCompileShader(shader);
            if (glGetShaderi(shader, GL_COMPILE_STATUS) != GL_TRUE) {
                throw new IllegalStateException("AuramPhoton LPV " + label + " compile failed:\n" + glGetShaderInfoLog(shader));
            }
            glAttachShader(program, shader);
        } finally {
            glDeleteShader(shader);
        }
    }
}
