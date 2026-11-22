/*
--------------------------------------------------------------------------------

  Photon Shader by SixthSurge

  program/post/bloom/gaussian0.fsh
  1D horizontal gaussian blur pass for bloom tiles

--------------------------------------------------------------------------------
*/

#include "/include/global.glsl"

#include "/include/post_processing/bloom_atlas.glsl"

layout(location = 0) out vec3 bloom_tiles;

/* RENDERTARGETS: 0 */

in vec2 uv;

uniform sampler2D colortex0;

uniform vec2 view_res;

#include "/include/post_processing/bloom_gaussian.glsl"

void main() {
    if (bloom_small_atlas(view_res)) {
        bloom_tiles = bloom_blur_small_tile(colortex0, view_res, gl_FragCoord.xy, ivec2(1, 0));
        return;
    }

    ivec2 texel = ivec2(gl_FragCoord.xy);

    // Calculate the bounds of the tile containing the fragment

    int tile_index = int(-log2(1.0 - uv.x));

    float tile_scale = bloom_tile_scale(tile_index);
    vec2 tile_offset = bloom_tile_offset(tile_index);

    ivec2 bounds_min = ivec2(view_res * tile_offset);
    ivec2 bounds_max = ivec2(view_res * (tile_offset + tile_scale));

    // Discard fragments that aren't part of a bloom tile

    if (clamp(texel.y, bounds_min.y, bounds_max.y) != texel.y
        || tile_index > 5) {
        discard;
        return;
    }

    // Horizontal 9-tap gaussian blur

    bloom_tiles = vec3(0.0);
    float weight_sum = 0.0;

    for (int i = -4; i <= 4; ++i) {
        ivec2 pos = texel + ivec2(i, 0);
        ivec2 tap = pos;
        tap.x = clamp(pos.x, bounds_min.x + 2, bounds_max.x - 2);
        float weight = binomial_weights_9[abs(i)] * float(tap.x == pos.x);
        bloom_tiles += texelFetch(colortex0, tap, 0).rgb * weight;
        weight_sum += weight;
    }

    bloom_tiles /= weight_sum;
}
