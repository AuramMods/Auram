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

layout(location = 0) out vec3 bloom_tile;

/* RENDERTARGETS: 0 */

in vec2 uv;

uniform vec2 view_pixel_size;
uniform vec2 view_res;

#if BLOOM_TILE_INDEX == 0
// Initial tile reads TAA output directly
const float src_tile_scale = 1.0;
const vec2 src_tile_offset = vec2(0.0);
uniform sampler2D colortex5;
#define SRC_SAMPLER colortex5
#else
// Subsequent tiles read from colortex0
const float src_tile_scale = bloom_tile_scale(BLOOM_TILE_INDEX - 1);
const vec2 src_tile_offset = bloom_tile_offset(BLOOM_TILE_INDEX - 1);
uniform sampler2D colortex0;
#define SRC_SAMPLER colortex0
#endif

vec3 bloom_tap(vec2 uv_src, vec2 offset, vec4 centers, bool bounded) {
    vec2 coord = uv_src + offset * view_pixel_size;
    if (bounded) coord = clamp(coord, centers.xy, centers.zw);
    return textureLod(SRC_SAMPLER, coord, 0).rgb;
}

vec3 downsample_bloom(vec2 uv_src, vec4 centers, bool bounded) {
    // 6x6 downsampling filter made from overlapping 4x4 box kernels.
    vec3 color = bloom_tap(uv_src, vec2( 0.0,  0.0), centers, bounded) * 0.125;
    color += bloom_tap(uv_src, vec2( 1.0,  1.0), centers, bounded) * 0.125;
    color += bloom_tap(uv_src, vec2(-1.0,  1.0), centers, bounded) * 0.125;
    color += bloom_tap(uv_src, vec2( 1.0, -1.0), centers, bounded) * 0.125;
    color += bloom_tap(uv_src, vec2(-1.0, -1.0), centers, bounded) * 0.125;
    color += bloom_tap(uv_src, vec2( 2.0,  0.0), centers, bounded) * 0.0625;
    color += bloom_tap(uv_src, vec2(-2.0,  0.0), centers, bounded) * 0.0625;
    color += bloom_tap(uv_src, vec2( 0.0,  2.0), centers, bounded) * 0.0625;
    color += bloom_tap(uv_src, vec2( 0.0, -2.0), centers, bounded) * 0.0625;
    color += bloom_tap(uv_src, vec2( 2.0,  2.0), centers, bounded) * 0.03125;
    color += bloom_tap(uv_src, vec2(-2.0,  2.0), centers, bounded) * 0.03125;
    color += bloom_tap(uv_src, vec2( 2.0, -2.0), centers, bounded) * 0.03125;
    color += bloom_tap(uv_src, vec2(-2.0, -2.0), centers, bounded) * 0.03125;
    return color;
}

void main() {
    if (bloom_small_atlas(view_res)) {
#if BLOOM_TILE_INDEX == 0
        vec4 edges = vec4(0.0, 0.0, view_res);
#else
        vec4 edges = bloom_tile_edges(BLOOM_TILE_INDEX - 1, view_res);
#endif
        vec2 uv_src = mix(edges.xy, edges.zw, uv) * view_pixel_size;
        bloom_tile = downsample_bloom(uv_src, bloom_tile_centers(edges, view_pixel_size), true);
        return;
    }

    vec2 pad_amount = 3.0 * view_pixel_size * rcp(src_tile_scale);
    vec2 uv_src = clamp(uv, pad_amount, 1.0 - pad_amount) * src_tile_scale
        + src_tile_offset;
    bloom_tile = downsample_bloom(uv_src, vec4(0.0), false);
}
