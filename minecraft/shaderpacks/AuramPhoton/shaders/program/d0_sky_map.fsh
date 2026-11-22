/*
--------------------------------------------------------------------------------

  Photon Shader by SixthSurge

  program/d0_sky_map:
  Render omnidirectional sky map for reflections and SH lighting

--------------------------------------------------------------------------------
*/

#include "/include/global.glsl"

layout(location = 0) out vec3 sky_map;

/* RENDERTARGETS: 4 */

in vec2 uv;

flat in vec3 ambient_color_vertex;
vec3 ambient_color;
flat in vec3 light_color;

#if defined WORLD_OVERWORLD
flat in vec3 sun_color;
flat in vec3 moon_color;
flat in vec3 sky_color_vertex;
vec3 sky_color;

flat in mat2x4 aurora_data;

float aurora_amount;
mat2x3 aurora_colors;
float rainbow_amount;

#include "/include/sky/clouds/parameters.glsl"
flat in CloudsParameters clouds_params;

#include "/include/fog/overworld/parameters.glsl"
flat in OverworldFogParameters fog_params;
#endif

// ------------
//   Uniforms
// ------------

uniform sampler3D colortex6; // 3D bubbly worley noise
#define SAMPLER_WORLEY_BUBBLY colortex6
uniform sampler3D colortex7; // 3D swirley worley noise
#define SAMPLER_WORLEY_SWIRLEY colortex7

#if defined WORLD_OVERWORLD && defined GALAXY
uniform sampler2D colortex13;
#define galaxy_sampler colortex13
#endif

uniform sampler3D depthtex0; // atmospheric scattering LUT
uniform sampler2D depthtex1;

uniform sampler2D colortex8; // cloud shadow map

uniform sampler2D noisetex;

uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;

uniform mat4 shadowModelView;
uniform mat4 shadowModelViewInverse;
uniform mat4 shadowProjection;
uniform mat4 shadowProjectionInverse;

uniform vec3 cameraPosition;
uniform vec3 previousCameraPosition;

uniform float near;
uniform float far;

uniform int worldTime;
uniform int moonPhase;
uniform float sunAngle;

uniform int frameCounter;
uniform float frameTimeCounter;

uniform int isEyeInWater;
uniform float eyeAltitude;
uniform float rainStrength;
uniform float blindness;
uniform float darknessFactor;

uniform vec3 light_dir;
uniform vec3 sun_dir;
uniform vec3 moon_dir;

uniform vec2 view_res;
uniform vec2 view_pixel_size;
uniform vec2 taa_offset;

uniform float world_age;
uniform float eye_skylight;

uniform float time_sunrise;
uniform float time_noon;
uniform float time_sunset;
uniform float time_midnight;

uniform float biome_cave;
uniform float biome_may_snow;

// ------------
//   Includes
// ------------

#define ATMOSPHERE_SCATTERING_LUT depthtex0
#define CLOUDS_USE_LOCAL_COVERAGE_MAP

#ifdef CLOUDS_CUMULUS_PRECOMPUTE_LOCAL_COVERAGE
#define CLOUDS_USE_LOCAL_COVERAGE_MAP
#endif

#if defined WORLD_OVERWORLD
#include "/include/fog/overworld/analytic.glsl"
#include "/include/sky/aurora.glsl"
#endif

#include "/include/sky/projection.glsl"
#include "/include/sky/sky.glsl"
#ifdef AURAM_PHOTON_RASTER_LPV
#include "/include/lighting/lpv/light_colors.glsl"
#endif

void main() {
#if defined WORLD_OVERWORLD
    aurora_amount = aurora_data[0].w;
    aurora_colors = mat2x3(aurora_data[0].xyz, aurora_data[1].xyz);
    rainbow_amount = aurora_data[1].w;
#endif

    ivec2 texel = ivec2(gl_FragCoord.xy);
    vec2 sky_uv = uv;
    vec3 ground_ambient = ambient_color_vertex;
    ambient_color = ambient_color_vertex;
#ifdef WORLD_OVERWORLD
    sky_color = sky_color_vertex;
    ambient_color = sky_color_vertex;
    float aurora_gain = 1.0;
#endif

#ifdef AURAM_GROUND_SKY_MAP
    // SH only samples the upper hemisphere. The first two rows above the
    // visible map extend its last row for linear and bicubic filtering.
    if (texel.y >= sky_map_res.y) {
        int ground_y = texel.y - sky_map_res.y;
        if (ground_y < 2) {
            texel.y = sky_map_res.y - 1;
            sky_uv.y = (float(texel.y) + 0.5) / float(sky_map_res.y);
        } else {
#if defined WORLD_OVERWORLD
            if (ground_y < sky_map_res.y / 2 - 1 || texel.x == sky_map_res.x) {
                sky_map = vec3(0.0);
                return;
            }
            texel.y = ground_y;
            sky_uv.y = uv.y * 2.0 - 1.0;
            aurora_gain = AURORA_GROUND_LIGHTING;
            sky_color = ambient_color_vertex + aurora_gain
                * (mix(aurora_colors[0], aurora_colors[1], 0.25) * aurora_amount);
            ambient_color = sky_color;
#else
            sky_map = vec3(0.0);
            return;
#endif
        }
    } else {
        sky_uv.y *= 2.0;
    }
#ifdef WORLD_OVERWORLD
    ground_ambient += AURORA_GROUND_LIGHTING
        * (mix(aurora_colors[0], aurora_colors[1], 0.25) * aurora_amount);
#endif
#endif

    if (texel.x == sky_map_res.x) { // Store lighting colors
        sky_map = vec3(0.0);
        switch (texel.y) {
            case 0:
                sky_map = light_color;
                break;

            case 1:
                sky_map = ground_ambient;
                break;
        }
#ifdef AURAM_PHOTON_RASTER_LPV
        int light_index = texel.y - lpv_handheld_palette_row;
        if (light_index >= 0 && light_index < 48) {
            sky_map = lpv_light_colors[light_index];
        }
#endif
    } else { // Draw sky map
        vec3 ray_dir = unproject_sky(sky_uv);
#ifdef WORLD_OVERWORLD
        sky_map = draw_sky(ray_dir, texel, aurora_gain);
#else
        sky_map = draw_sky(ray_dir);
#endif

#if defined WORLD_OVERWORLD
        // Apply analytic fog over sky
        mat2x3 fog = air_fog_analytic(
            cameraPosition,
            cameraPosition + ray_dir,
            true,
            eye_skylight,
            1.0
        );
        sky_map = sky_map * fog[1] + fog[0];
#endif
    }
}
