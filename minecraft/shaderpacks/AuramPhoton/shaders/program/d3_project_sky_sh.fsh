#include "/include/global.glsl"
#include "/include/lighting/colors/sky_sh_projection.glsl"

layout(location = 0) out vec4 sky_map;

/* RENDERTARGETS: 4 */

void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);
    int index = texel.y - 2;
    if (texel.x != textureSize(colortex4, 0).x - 1 || index < 0 || index > 9) {
        sky_map = texelFetch(colortex4, texel, 0);
        return;
    }

    const int sample_count = 256;
    float skylight_boost = get_skylight_boost();
    vec3 sums[9];
    for (int band = 0; band < 9; ++band) {
        sums[band] = vec3(0.0);
    }

    // The nine coefficients and upward irradiance use the current sky map and
    // the same samples and basis as the compute path.
    for (int i = 0; i < sample_count; ++i) {
        vec3 radiance;
        mat3 coefficients;
        sample_sky_sh(i, skylight_boost, radiance, coefficients);
        int first = index == 9 ? 0 : index;
        int last = index == 9 ? 8 : index;
        for (int band = first; band <= last; ++band) {
            sums[band] += radiance * coefficients[band / 3][band % 3]
                * (tau / float(sample_count));
        }
    }

    if (index < 9) {
        sky_map = vec4(sums[index], 0.0);
    } else {
        sh3 harmonics = sh3(
            mat3(sums[0], sums[1], sums[2]),
            mat3(sums[3], sums[4], sums[5]),
            mat3(sums[6], sums[7], sums[8])
        );
        sky_map = vec4(sh_evaluate_irradiance(harmonics, vec3(0.0, 1.0, 0.0), 1.0), 0.0);
    }
}
