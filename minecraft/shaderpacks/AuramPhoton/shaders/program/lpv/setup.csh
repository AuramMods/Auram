/*
--------------------------------------------------------------------------------

  Photon Shader by SixthSurge

  program/lpv/setup.csh:
  Store light colors and tint colors in SSBO

--------------------------------------------------------------------------------
*/

#include "/include/global.glsl"

layout(local_size_x = 48) in;
const ivec3 workGroups = ivec3(1, 1, 1);

writeonly uniform image2D light_data_img;

#include "/include/lighting/lpv/light_colors.glsl"

void main() {
    int index = int(gl_LocalInvocationID.x);

    imageStore(
        light_data_img,
        ivec2(index, 0),
        vec4(lpv_light_colors[index], 0.0)
    );
    imageStore(
        light_data_img,
        ivec2(index, 1),
        vec4(get_lpv_tint_or_crystal_color(index), 0.0)
    );
}

#ifndef COLORED_LIGHTS
#error "This program should be disabled if colored lights are disabled"
#endif

// Register the separately compiled companion stage in Oculus's include graph.
#if 0
#include "/program/lpv/raster_palette.fsh"
#endif
