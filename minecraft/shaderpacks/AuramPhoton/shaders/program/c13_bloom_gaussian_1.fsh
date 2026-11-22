/*
--------------------------------------------------------------------------------

  Photon Shader by SixthSurge

  program/post/bloom/gaussian0.fsh
  1D vertical gaussian blur pass for bloom tiles

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
        bloom_tiles = bloom_blur_small_tile(colortex0, view_res, gl_FragCoord.xy, ivec2(0, 1));
        return;
    }

    ivec2 texel = ivec2(gl_FragCoord.xy);

    // Calculate the bounds of the tile containing the fragment

    float a = -log2(1.0 - uv.x);
    int tile_index = int(a);

    float tile_scale = bloom_tile_scale(tile_index);
    vec2 tile_offset = bloom_tile_offset(tile_index);

    ivec2 bounds_min = ivec2(view_res * tile_offset);
    ivec2 bounds_max = ivec2(view_res * (tile_offset + tile_scale));

    // Apply padding around bloom tiles

    if (clamp(texel.y, bounds_min.y, bounds_max.y) != texel.y
        || tile_index > 5) {
        // Get index of closest tile
        int closest_tile = (uv.y < 0.66)
            ? int(0.5 * a + 0.25) * 2
            : int(0.5 * a - 0.25) * 2 + 1;

        // Get bounds of closest tile
        float closest_scale = bloom_tile_scale(closest_tile);
        vec2 closest_offset = bloom_tile_offset(closest_tile);
        ivec2 closest_bounds_min = ivec2(view_res * closest_offset + 1);
        ivec2 closest_bounds_max
            = ivec2(view_res * (closest_offset + closest_scale) - 1);

        // Padding can point to a tile smaller than the one-pixel inset.
        ivec2 last_texel = ivec2(view_res) - 1;
        closest_bounds_min = min(closest_bounds_min, last_texel);
        closest_bounds_max = clamp(closest_bounds_max, closest_bounds_min, last_texel);
        bloom_tiles = texelFetch(colortex0, clamp(texel, closest_bounds_min, closest_bounds_max), 0).rgb;

        return;
    }

    // Vertical 9-tap gaussian blur

    bloom_tiles = vec3(0.0);
    float weight_sum = 0.0;

    for (int i = -4; i <= 4; ++i) {
        ivec2 pos = texel + ivec2(0, i);
        ivec2 tap = pos;
        tap.y = clamp(pos.y, bounds_min.y + 2, bounds_max.y - 2);
        float weight = binomial_weights_9[abs(i)] * float(tap.y == pos.y);
        bloom_tiles += texelFetch(colortex0, tap, 0).rgb * weight;
        weight_sum += weight;
    }

    bloom_tiles /= weight_sum;
}
