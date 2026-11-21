#ifndef INCLUDE_BLOOM_GAUSSIAN
#define INCLUDE_BLOOM_GAUSSIAN

const float[5] binomial_weights_9
    = float[5](0.2734375, 0.21875, 0.109375, 0.03125, 0.00390625);

vec3 bloom_blur_small_tile(sampler2D atlas, vec2 resolution, vec2 pixel, ivec2 axis) {
    int index = bloom_tile_at_x(pixel.x, resolution.x);
    vec4 edges = bloom_tile_edges(index, resolution);
    if (any(lessThan(pixel, edges.xy)) || any(greaterThanEqual(pixel, edges.zw))) {
        discard;
        return vec3(0.0);
    }

    ivec2 lower = ivec2(edges.xy);
    ivec2 upper = ivec2(edges.zw) - 1;
    vec3 color = vec3(0.0);
    for (int i = -4; i <= 4; ++i) {
        ivec2 tap = clamp(ivec2(pixel) + i * axis, lower, upper);
        color += texelFetch(atlas, tap, 0).rgb * binomial_weights_9[abs(i)];
    }
    return color;
}

#endif
