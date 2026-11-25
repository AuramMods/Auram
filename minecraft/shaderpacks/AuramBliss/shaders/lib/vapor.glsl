#ifdef VOLUMETRIC_VAPOR
#ifdef AURAM_VAPOR_BACKEND
#define AURAM_RENDER_VAPOR

uniform sampler3D auramVaporVolume;
uniform bool auramVaporActive;
uniform float auramVaporBlend;
uniform vec3 auramVaporOrigin;
uniform vec3 auramVaporMinimum;
uniform vec3 auramVaporMaximum;
#ifdef VAPOR_BEHIND_TRANSLUCENT
float vaporTranslucentStart = 0.0;
#endif

vec2 vaporRayInterval(vec3 origin, vec3 direction, float rayEnd) {
    if (!auramVaporActive) return vec2(0.0);
    #if VAPOR_RENDER_RANGE > 0
        rayEnd = min(rayEnd, float(VAPOR_RENDER_RANGE));
    #endif
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
    float first = max(max(entry.x, entry.y), max(entry.z, 0.0));
    #ifdef VAPOR_BEHIND_TRANSLUCENT
        first = max(first, vaporTranslucentStart);
    #endif
    return vec2(first, min(min(exit.x, exit.y), min(exit.z, rayEnd)));
}

float vaporDetailNoise(vec3 position) {
    vec3 cell = floor(position);
    vec3 blend = fract(position);
    blend = blend * blend * (3.0 - 2.0 * blend);
    // Use two slices of the existing noise texture as a repeatable 3D field.
    vec2 base = cell.xz + blend.xz + cell.y * vec2(37.0, 151.0) + 0.5;
    float lower = textureLod(noisetex, base / 512.0, 0.0).a;
    float upper = textureLod(noisetex, (base + vec2(37.0, 151.0)) / 512.0, 0.0).a;
    return mix(lower, upper, blend.y);
}

float vaporDensity(vec3 playerPos) {
    vec3 local = playerPos - auramVaporOrigin;
    ivec3 cell = clamp(ivec3(floor(local)), ivec3(0), ivec3(63));
    // Full collision cells mask the trilinear filter at glass and walls.
    if (texelFetch(auramVaporVolume, cell, 0).b < 0.5) return 0.0;
    vec2 densityFrames = textureLod(auramVaporVolume, local / 64.0, 0.0).rg;
    float density = mix(densityFrames.x, densityFrames.y, auramVaporBlend);
    if (density <= 0.001) return 0.0;

    // Warp the detail coordinates, keeping the density sample inside its world cell.
    vec3 field = (playerPos + cameraPosition) * vec3(1.8, 1.0, 1.8);
    field.y -= frameTimeCounter * 0.8;
    field *= VAPOR_WISP_SCALE / 3.0;
    float bend = vaporDetailNoise(field * 0.72) - 0.5;
    float detail = vaporDetailNoise(field * 2.1 + bend * vec3(2.4, 0.75, -2.0));
    float wisps = smoothstep(0.27, 0.75, detail);
    float edge = 1.0 - smoothstep(0.1, 0.5, density);
    return density * mix(1.0, mix(0.22, 1.45, wisps), edge);
}

struct VaporScattering {
    vec3 ambient;
    vec3 blockLight;
    float transmission;
};

VaporScattering traceVapor(vec3 origin, vec3 direction, vec2 interval, float start, float stop, vec3 ambient, inout float rayTransmission) {
    float first = max(start, interval.x);
    float distance = min(stop, interval.y) - first;
    if (distance <= 0.0) return VaporScattering(vec3(0.0), vec3(0.0), 1.0);

    #if VAPOR_MAX_OPACITY < 100
        float minimumTransmission = (1.0 - VAPOR_MAX_OPACITY / 100.0) / max(rayTransmission, 1e-6);
        if (minimumTransmission >= 1.0) return VaporScattering(vec3(0.0), vec3(0.0), 1.0);
    #endif
    int samples = min(VAPOR_MAX_SAMPLES, int(ceil(distance / VAPOR_SAMPLE_SPACING)));
    float stride = distance / float(samples);
    float transmission = 1.0;
    vec3 scattering = vec3(0.0);
    #if (defined LPV_VL_FOG_ILLUMINATION && defined IS_LPV_ENABLED && defined EXCLUDE_WRITE_TO_LUT) || defined VAPOR_TRANSLUCENT_LPV
        float blockLightScale = (LPV_VL_FOG_ILLUMINATION_BRIGHTNESS / 100.0) * VAPOR_LIGHT_BRIGHTNESS;
        #ifdef OVERWORLD_SHADER
            float night = smoothstep(0.0, 0.05, -sunElevation);
            blockLightScale *= mix(1.0, VAPOR_NIGHT_LIGHT, night);
            // Skylight also identifies dark interiors during the day.
            float skyExposure = smoothstep(64.0, 192.0, float(eyeBrightnessSmooth.y));
            float vaporDarkness = max(night, 1.0 - skyExposure);
        #else
            const float vaporDarkness = 1.0;
        #endif
    #endif
    for (int i = 0; i < samples; i++) {
        vec3 position = origin + direction * (first + (float(i) + 0.5) * stride);
        float density = vaporDensity(position);
        if (density <= 0.001) continue;
        float segmentTransmission = exp(-VAPOR_EXTINCTION * density * stride);
        #if VAPOR_MAX_OPACITY < 100
            // Spend the remaining opacity budget before accumulating this sample's light.
            segmentTransmission = max(segmentTransmission, minimumTransmission / transmission);
        #endif
        #if (defined LPV_VL_FOG_ILLUMINATION && defined IS_LPV_ENABLED && defined EXCLUDE_WRITE_TO_LUT) || defined VAPOR_TRANSLUCENT_LPV
            vec3 lpv = GetLpvPosition(position);
            if (all(greaterThanEqual(lpv, vec3(0.5))) && all(lessThan(lpv, LpvSize3 - 0.5))) {
                vec3 light = GetLpvBlockLight(SampleLpvLinear(lpv)) * blockLightScale;
                #if VAPOR_LIGHT_LIMIT > 0
                    float peak = max(max(light.r, light.g), light.b);
                    light *= min(1.0, float(VAPOR_LIGHT_LIMIT) / max(peak, 1e-6));
                #endif
                #if VAPOR_LIGHT_SATURATION != 100
                    float neutral = dot(light, vec3(0.2126, 0.7152, 0.0722));
                    light = max(mix(vec3(neutral), light, VAPOR_LIGHT_SATURATION / 100.0), vec3(0.0));
                #endif
                scattering += light * ((1.0 - segmentTransmission) * transmission);
            }
        #endif
        transmission *= segmentTransmission;
        if (transmission < 0.002) break;
        #if VAPOR_MAX_OPACITY < 100
            if (transmission <= minimumTransmission) break;
        #endif
    }
    #if VAPOR_MAX_OPACITY < 100
        rayTransmission *= transmission;
    #endif
    vec3 ambientScattering = ambient * (1.0 - transmission);
    #if (defined LPV_VL_FOG_ILLUMINATION && defined IS_LPV_ENABLED && defined EXCLUDE_WRITE_TO_LUT) || defined VAPOR_TRANSLUCENT_LPV
        // Let nearby colored lights dominate the smoke without boosting the neutral part of the light.
        float neutral = min(min(scattering.r, scattering.g), scattering.b);
        vec3 extraColor = VAPOR_LIGHT_CHROMA * (scattering - neutral);
        float colorStrength = max(max(extraColor.r, extraColor.g), extraColor.b);
        float ambientStrength = max(max(ambientScattering.r, ambientScattering.g), ambientScattering.b);
        ambientScattering *= 1.0 - VAPOR_AMBIENT_SUPPRESSION * colorStrength / max(ambientStrength + colorStrength, 1e-6);
        scattering = (scattering + extraColor) * mix(1.0, VAPOR_DARK_GLOW, vaporDarkness);
    #endif
    return VaporScattering(ambientScattering, scattering, transmission);
}
#endif
#endif
