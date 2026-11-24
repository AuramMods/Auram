#ifdef VOLUMETRIC_VAPOR
#ifdef AURAM_VAPOR_BACKEND
#define AURAM_RENDER_VAPOR

uniform sampler3D auramVaporVolume;
uniform bool auramVaporActive;
uniform float auramVaporBlend;
uniform vec3 auramVaporOrigin;
uniform vec3 auramVaporMinimum;
uniform vec3 auramVaporMaximum;

vec2 vaporRayInterval(vec3 origin, vec3 direction, float rayEnd) {
    if (!auramVaporActive) return vec2(0.0);
    bvec3 parallel = lessThan(abs(direction), vec3(1e-6));
    bvec3 inside = bvec3(origin.x >= auramVaporMinimum.x && origin.x < auramVaporMaximum.x,
                         origin.y >= auramVaporMinimum.y && origin.y < auramVaporMaximum.y,
                         origin.z >= auramVaporMinimum.z && origin.z < auramVaporMaximum.z);
    if ((parallel.x && !inside.x) || (parallel.y && !inside.y) || (parallel.z && !inside.z)) return vec2(0.0);
    vec3 directionSign = mix(vec3(-1.0), vec3(1.0), step(vec3(0.0), direction));
    vec3 inverseDirection = directionSign / max(abs(direction), vec3(1e-6));
    vec3 a = (auramVaporMinimum - origin) * inverseDirection;
    vec3 b = (auramVaporMaximum - origin) * inverseDirection;
    vec3 nearPlane = min(a, b);
    vec3 farPlane = max(a, b);
    vec3 entry = vec3(parallel.x ? 0.0 : nearPlane.x, parallel.y ? 0.0 : nearPlane.y, parallel.z ? 0.0 : nearPlane.z);
    vec3 exit = vec3(parallel.x ? rayEnd : farPlane.x, parallel.y ? rayEnd : farPlane.y, parallel.z ? rayEnd : farPlane.z);
    return vec2(max(max(entry.x, entry.y), max(entry.z, 0.0)),
                min(min(exit.x, exit.y), min(exit.z, rayEnd)));
}

float vaporDensity(vec3 playerPos) {
    vec3 local = playerPos - auramVaporOrigin;
    ivec3 cell = clamp(ivec3(floor(local)), ivec3(0), ivec3(63));
    // Full collision cells mask the trilinear filter at glass and walls.
    if (texelFetch(auramVaporVolume, cell, 0).b < 0.5) return 0.0;
    vec2 density = textureLod(auramVaporVolume, local / 64.0, 0.0).rg;
    return mix(density.x, density.y, auramVaporBlend);
}

vec4 traceVapor(vec3 origin, vec3 direction, vec2 interval, float start, float stop, vec3 ambient) {
    float first = max(start, interval.x);
    float distance = min(stop, interval.y) - first;
    if (distance <= 0.0) return vec4(0.0, 0.0, 0.0, 1.0);

    int samples = min(48, int(ceil(distance / 0.65)));
    float stride = distance / float(samples);
    float transmission = 1.0;
    vec3 scattering = vec3(0.0);
    for (int i = 0; i < samples; i++) {
        vec3 position = origin + direction * (first + (float(i) + 0.5) * stride);
        float density = vaporDensity(position);
        if (density <= 0.001) continue;
        vec3 light = ambient;
        #if defined LPV_VL_FOG_ILLUMINATION && defined IS_LPV_ENABLED && defined EXCLUDE_WRITE_TO_LUT
            vec3 lpv = GetLpvPosition(position);
            if (all(greaterThanEqual(lpv, vec3(0.5))) && all(lessThan(lpv, LpvSize3 - 0.5))) {
                light += GetLpvBlockLight(SampleLpvLinear(lpv)) * (LPV_VL_FOG_ILLUMINATION_BRIGHTNESS / 100.0);
            }
        #endif
        float segmentTransmission = exp(-2.5 * density * stride);
        scattering += light * ((1.0 - segmentTransmission) * transmission);
        transmission *= segmentTransmission;
        if (transmission < 0.002) break;
    }
    return vec4(scattering, transmission);
}
#endif
#endif
