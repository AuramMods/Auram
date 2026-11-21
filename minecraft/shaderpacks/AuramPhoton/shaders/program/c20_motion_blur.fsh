/*
--------------------------------------------------------------------------------

  Photon Shader by SixthSurge

  program/c20_motion_blur:
  Apply motion blur

--------------------------------------------------------------------------------
*/

#include "/include/global.glsl"

layout(location = 0) out vec3 scene_color;

/* RENDERTARGETS: 0 */

in vec2 uv;

// ------------
//   Uniforms
// ------------

uniform sampler2D colortex0; // Scene color

uniform sampler2D depthtex0;

uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;

uniform mat4 gbufferPreviousModelView;
uniform mat4 gbufferPreviousProjection;

uniform vec3 cameraPosition;
uniform vec3 previousCameraPosition;

uniform float frameTime;
uniform float near;
uniform float far;

uniform vec2 view_res;
uniform vec2 view_pixel_size;
uniform vec2 taa_offset;

#define TEMPORAL_REPROJECTION
#include "/include/utility/space_conversion.glsl"

#define MOTION_BLUR_SAMPLES 20

void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);
    ivec2 view_texel = ivec2(gl_FragCoord.xy * taau_render_scale);

    float depth = texelFetch(depthtex0, view_texel, 0).x;
    vec3 color_sum = texelFetch(colortex0, texel, 0).rgb;

    // Only vanilla hand depth is encoded in the hand range.
    if (depth <= hand_depth || MOTION_BLUR_INTENSITY == 0.0) {
        scene_color = color_sum;
        return;
    }

    bool is_lod = false;
#ifdef LOD_MOD_ACTIVE
    if (depth >= 1.0) {
        ivec2 lod_texel = ivec2(gl_FragCoord.xy * lod_depth_tex_scale);
        float lod_depth = texelFetch(lod_depth_tex, lod_texel, 0).x;
        is_lod = is_lod_terrain(depth, lod_depth);
        if (is_lod) depth = lod_depth;
    }
#endif
    vec3 view_position = screen_to_view_space(vec3(uv, depth), false, is_lod);
    vec3 scene_position = view_to_scene_space(view_position);
    vec2 velocity = uv - reproject_scene_space(scene_position, false, is_lod).xy;
    vec2 increment
        = (0.5 * MOTION_BLUR_INTENSITY / float(MOTION_BLUR_SAMPLES)) * velocity;

    // The center sample is already included. A straight ray cannot re-enter
    // the viewport, and invalid projected coordinates must never reach texelFetch.
    vec2 pos = uv + increment;
    float weight_sum = 1.0;
    for (int i = 1; i < MOTION_BLUR_SAMPLES; ++i, pos += increment) {
        if (!(all(greaterThanEqual(pos, vec2(0.0))) && all(lessThan(pos, vec2(1.0)))))
            break;

        ivec2 view_tap = ivec2(pos * view_res * taau_render_scale);
        if (texelFetch(depthtex0, view_tap, 0).x > hand_depth) {
            ivec2 tap = ivec2(pos * view_res);
            color_sum += texelFetch(colortex0, tap, 0).rgb;
            weight_sum += 1.0;
        }
    }

    scene_color = color_sum * rcp(weight_sum);
}
