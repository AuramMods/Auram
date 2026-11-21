/*
--------------------------------------------------------------------------------

  Photon Shader by SixthSurge

  program/d4a_generate_sky_sh.csh:
  Generate skylight SH using parallel reduction

--------------------------------------------------------------------------------
*/

#ifdef MC_GL_KHR_shader_subgroup_arithmetic
#extension GL_KHR_shader_subgroup_arithmetic : enable
#endif

#include "/include/global.glsl"

layout(local_size_x = 256) in;

const ivec3 workGroups = ivec3(1, 1, 1);

layout(rgba16f) writeonly uniform image2D colorimg4;

#ifdef MC_GL_KHR_shader_subgroup_arithmetic
// 32 here is to match the *potential* number of subgroups in the workgroup. 
// On NVIDIA/AMD, it's likely we're wasting memory here, but it ensures we won't crash and die on Intel.
shared vec3 shared_memory[32][9];
#else
shared vec3 shared_memory[256][9];
#endif

#include "/include/lighting/colors/sky_sh_projection.glsl"

void main() {
#ifndef SH_SKYLIGHT
    return;
#endif

    const uint sample_count = 256;
    uint i = uint(gl_LocalInvocationID.x);

    // Calculate SH coefficients for each sample

    float skylight_boost = get_skylight_boost();

    vec3 radiance;
    mat3 coeff;
    sample_sky_sh(int(i), skylight_boost, radiance, coeff);

#ifdef MC_GL_KHR_shader_subgroup_arithmetic
    for (uint band = 0u; band < 9u; ++band) {
        float c = coeff[band / 3u][band % 3u];

        vec3 sum = subgroupAdd(radiance * c * (tau / float(sample_count)));

        if (gl_SubgroupInvocationID == 0u) {
            shared_memory[gl_SubgroupID][band] = sum;
        }
    }

    
    barrier();

    if (gl_SubgroupID != 0u) {
        return;
    }

    for (uint band = 0u; band < 9u; ++band) {
        vec3 sum = vec3(0.0);

        for (uint s = gl_SubgroupInvocationID; s < gl_NumSubgroups; s += gl_SubgroupSize) {
            sum += shared_memory[s][band];
        }

        sum = subgroupAdd(sum);

        if (gl_SubgroupInvocationID == 0u) {
            shared_memory[0][band] = sum;
        }
    }
#else
    for (uint band = 0u; band < 9u; ++band) {
        shared_memory[i][band]
            = radiance * coeff[band / 3u][band % 3u]
            * (tau / float(sample_count));
    }

    barrier();

    // Sum samples using parallel reduction

    /*
    for (uint stride = sample_count / 2u; stride > 0u; stride /= 2u) {
        if (i < stride) {
            for (uint band = 0u; band < 9u; ++band) {
                shared_memory[i][band] += shared_memory[i + stride][band];
            }
        }

        barrier();
    }
    */

    // Loop manually unrolled as Intel doesn't seem to like barrier() calls in loops
#define PARALLEL_REDUCTION_ITER(STRIDE) \
    if (i < (STRIDE)) { \
        for (uint band = 0u; band < 9u; ++band) { \
            shared_memory[i][band] += shared_memory[i + (STRIDE)][band]; \
        } \
    } \
    barrier();

    PARALLEL_REDUCTION_ITER(128u)
    PARALLEL_REDUCTION_ITER(64u)
    PARALLEL_REDUCTION_ITER(32u)
    PARALLEL_REDUCTION_ITER(16u)
    PARALLEL_REDUCTION_ITER(8u)
    PARALLEL_REDUCTION_ITER(4u)
    PARALLEL_REDUCTION_ITER(2u)
    PARALLEL_REDUCTION_ITER(1u)

#undef PARALLEL_REDUCTION_ITER
#endif

    // Save SH coeff in colorimg4

    if (i == 0u) {
        for (uint band = 0u; band < 9u; ++band) {
            vec3 sh_coeff = shared_memory[0][band];
            imageStore(colorimg4, ivec2(191, 2 + band), vec4(sh_coeff, 0.0));
        }

        // Store irradiance facing up for forward lighting

        sh3 sh;
        for (uint band = 0u; band < 3u; ++band) {
            sh.f1[band] = shared_memory[0][band];
            sh.f2[band] = shared_memory[0][band + 3u];
            sh.f3[band] = shared_memory[0][band + 6u];
        }

        vec3 irradiance_up
            = sh_evaluate_irradiance(sh, vec3(0.0, 1.0, 0.0), 1.0);

        imageStore(colorimg4, ivec2(191, 2 + 9), vec4(irradiance_up, 0.0));
    }
}
