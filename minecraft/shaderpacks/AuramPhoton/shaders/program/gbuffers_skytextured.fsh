/*
--------------------------------------------------------------------------------

  Photon Shader by SixthSurge

  program/gbuffers_skytextured:
  Handle vanilla sun and moon and custom skies

--------------------------------------------------------------------------------
*/

#include "/include/global.glsl"

layout(location = 0) out vec3 frag_color;

/* RENDERTARGETS: 0 */

in vec2 uv;
in vec3 view_pos;

#if MC_VERSION >= 260100
in vec2 uv_mid;
#endif

flat in vec3 tint;
flat in vec3 sun_color;
flat in vec3 moon_color;

// ------------
//   Uniforms
// ------------

uniform sampler2D gtexture;
uniform sampler2D noisetex;

uniform int moonPhase;
uniform float rainStrength;
uniform int renderStage;

uniform vec3 view_sun_dir;

#include "/include/sky/atmosphere.glsl"
#include "/include/sky/astral_eclipse.glsl"
#include "/include/utility/color.glsl"

const float vanilla_sun_luminance = 10.0;
const float moon_luminance = 10.0;

void main() {
    vec2 new_uv = uv;

    if (renderStage == MC_RENDER_STAGE_CUSTOM_SKY) {
#ifdef CUSTOM_SKY
        frag_color = texture(gtexture, new_uv).rgb;
        frag_color = srgb_eotf_inv(frag_color) * rec709_to_working_color;
        frag_color *= CUSTOM_SKY_BRIGHTNESS;
#else
        frag_color = vec3(0.0);
#endif
    } else if (dot(view_pos, view_sun_dir) > 0.0) {
        // Sun

        // NB: not using renderStage to distinguish sun and moon because it's
        // broken in Iris for Minecraft 1.21.4

#ifdef VANILLA_SUN
        if (auramAstralSolarProgress > 0.0 && renderStage == MC_RENDER_STAGE_SUN) {
            // Astral supplies the atlas UVs for the current eclipse tile.
            vec3 corona = texture(gtexture, new_uv).rgb;
            if (all(equal(corona, vec3(0.0)))) discard;
            corona = srgb_eotf_inv(corona) * rec709_to_working_color;
            frag_color = corona * dot(corona, luminance_weights)
                * (sunlight_color * vanilla_sun_luminance) * sun_color / astral_solar_visibility();
            return;
        }

        // Cut out the sun itself (discard the halo around it)
        vec2 offset = uv * 2.0 - 1.0;
        if (max_of(abs(offset)) > 0.25) {
            discard;
        }

        frag_color = texture(gtexture, new_uv).rgb;
        frag_color = srgb_eotf_inv(frag_color) * rec709_to_working_color;
        frag_color *= dot(frag_color, luminance_weights)
            * (sunlight_color * vanilla_sun_luminance) * sun_color;
#else
        frag_color = vec3(0.0);
#endif
    } else {
        // Moon
#ifdef VANILLA_MOON
        frag_color
            = texture(gtexture, new_uv).rgb * vec3(MOON_R, MOON_G, MOON_B);
        frag_color *= tint;

        frag_color = srgb_eotf_inv(frag_color) * rec709_to_working_color;
        frag_color *= sunlight_color * moon_luminance;
#else
        frag_color = vec3(0.0);
#endif
    }
}
