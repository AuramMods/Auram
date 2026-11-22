ivec2 taa_last_rendered_texel(vec2 resolution) {
    // The scaled quad covers pixel centers strictly before its top and right edges.
    return max(ivec2(ceil(resolution * taau_render_scale - 0.5)) - 1, ivec2(0));
}
