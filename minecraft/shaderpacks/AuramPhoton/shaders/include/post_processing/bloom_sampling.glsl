#ifndef INCLUDE_BLOOM_SAMPLING
#define INCLUDE_BLOOM_SAMPLING

#include "/include/utility/bicubic.glsl"

// Expand the saved filter option before choosing its bounds-aware overload.
#define bloom_filter_name_impl(filter) bloom_sample_##filter
#define bloom_filter_name(filter) bloom_filter_name_impl(filter)
#define sample_bloom_tile bloom_filter_name(BLOOM_UPSAMPLING_FILTER)

vec3 bloom_sample_texture(sampler2D atlas, vec2 uv, vec4 centers) {
    return textureLod(atlas, clamp(uv, centers.xy, centers.zw), 0).rgb;
}

vec3 bloom_sample_bicubic_filter(sampler2D atlas, vec2 uv, vec4 centers) {
    return bicubic_filter_bounded(atlas, uv, 0, centers, true).rgb;
}

#endif
