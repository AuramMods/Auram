ivec2 taa_last_rendered_texel(vec2 resolution) {
    // The scaled quad covers pixel centers strictly before its top and right edges.
    // Render scale is set in whole percentages. Preserve exact half-pixel ties.
    const int scale_percent = int(taau_render_scale * 100.0 + 0.5);
    ivec2 covered = (ivec2(resolution) * scale_percent + 49) / 100;
    return max(covered - 1, ivec2(0));
}
