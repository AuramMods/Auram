package dev.auram.photon.compat.lpv;

import static net.irisshaders.iris.compat.sodium.impl.IrisChunkShaderBindingPoints.*;
import static org.lwjgl.opengl.GL41C.*;

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

    private static final String VOXEL_POINTS = VERSION + """
            layout(triangles) in;
            layout(points, max_vertices = 3) out;
            flat in ivec4 auram_photon_voxel[];
            flat out uint material;
            uniform int volume_side;
            void main() {
                for (int i = 0; i < 3; ++i) {
                    ivec4 voxel = auram_photon_voxel[i];
                    if (voxel.w == 0 || any(lessThan(voxel.xyz, ivec3(0)))
                            || any(greaterThanEqual(voxel.xyz, ivec3(volume_side)))) continue;
                    vec2 ndc = (vec2(voxel.xy) + 0.5) * (2.0 / float(volume_side)) - 1.0;
                    gl_Position = vec4(ndc, 0, 1);
                    gl_PointSize = 1.0;
                    gl_Layer = voxel.z;
                    material = uint(voxel.w);
                    EmitVertex();
                    EndPrimitive();
                }
            }
            """;

    private static final String VOXEL_MATERIAL = VERSION + """
            flat in uint material;
            layout(location = 0) out uint occupancy;
            void main() { occupancy = material; }
            """;

    private LightVolumeShaders() {}

    static int occupancy(String vertex, int side) {
        int program = link("occupancy", vertex, VOXEL_POINTS, VOXEL_MATERIAL, true);
        glProgramUniform1i(program, glGetUniformLocation(program, "volume_side"), side);
        return program;
    }

    static int transport(String fragment) {
        int program = link("transport", FULLSCREEN_VERTEX, VOLUME_LAYERS, VERSION + fragment, false);
        glProgramUniform1i(program, glGetUniformLocation(program, "previous_light"), 0);
        glProgramUniform1i(program, glGetUniformLocation(program, "voxel_sampler"), 1);
        glProgramUniform1i(program, glGetUniformLocation(program, "light_data_sampler"), 2);
        return program;
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
            glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) != GL_TRUE) {
                throw new IllegalStateException("AuramPhoton LPV " + label + " link failed:\n" + glGetProgramInfoLog(program));
            }
            return program;
        } catch (RuntimeException | Error failure) {
            glDeleteProgram(program);
            throw failure;
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
