#if !defined INCLUDE_MISC_DH_OVERDRAW
#define INCLUDE_MISC_DH_OVERDRAW

#include "/include/utility/dithering.glsl"

bool render_dh_overdraw(vec3 scene_pos, float vanilla_far, int frame_counter) {
    // AuramBSL ends the fade 12 blocks closer to the camera than the nominal overlap.
    const float fade_inset = 12.0;
    float fade_end = max0(vanilla_far - DH_OVERDRAW_DISTANCE - fade_inset);
    float fade_start = max0(fade_end - DH_OVERDRAW_FADE_LENGTH);
    float view_distance = length(scene_pos);

    if (view_distance >= fade_end) {
        return true;
    }
    if (view_distance <= fade_start) {
        return false;
    }

    float coverage = smoothstep(fade_start, fade_end, view_distance);
    return interleaved_gradient_noise(gl_FragCoord.xy, frame_counter) < coverage;
}

#endif // INCLUDE_MISC_DH_OVERDRAW
