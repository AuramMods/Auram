/*
--------------------------------------------------------------------------------

  Photon Shader by SixthSurge

  program/c5_c10_bloom_downsample
  Progressively downsample bloom tiles
  You must define BLOOM_TILE_INDEX before including this file

--------------------------------------------------------------------------------
*/

#include "/include/global.glsl"

#include "/include/post_processing/bloom_atlas.glsl"

out vec2 uv;

uniform vec2 view_res;

void main() {
    uv = gl_MultiTexCoord0.xy;

    vec2 vertex_pos = bloom_vertex_position(gl_Vertex.xy, BLOOM_TILE_INDEX, view_res);

    gl_Position = vec4(vertex_pos * 2.0 - 1.0, 0.0, 1.0);
}
