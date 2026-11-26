#include "/lib/vapor_bounds.glsl"

#ifdef VOLUMETRIC_VAPOR
#ifdef AURAM_VAPOR_BACKEND
#define AURAM_RENDER_VAPOR

uniform sampler3D auramVaporVolume;
uniform float auramVaporBlend;
uniform vec3 auramVaporOrigin;
uniform vec3 auramVaporMinimum;
uniform vec3 auramVaporMaximum;
uniform ivec3 cameraPositionInt;
uniform vec3 cameraPositionFract;
const float vaporNoisePeriod = 512.0;
const float vaporNoiseFixedPoint = 65536.0;
const uint vaporNoiseMask = uint(vaporNoisePeriod * vaporNoiseFixedPoint) - 1u;
const vec3 vaporBendScale = vec3(1.8, 1.0, 1.8) * (0.72 * VAPOR_WISP_SCALE / 3.0);
const vec3 vaporDetailScale = vec3(1.8, 1.0, 1.8) * (2.1 * VAPOR_WISP_SCALE / 3.0);
#ifdef AURAM_VAPOR_RADIUS
const float vaporRadius = float(AURAM_VAPOR_RADIUS);
#endif
#ifdef VAPOR_BEHIND_TRANSLUCENT
float vaporTranslucentStart = 0.0;
#endif

vec2 vaporRayInterval(vec3 origin, vec3 direction, float surfaceDistance) {
    if (!auramVaporActive) return vec2(0.0);
    vec2 cube = vaporCubeInterval(origin, direction, vaporCenter(), surfaceDistance);
    if (cube.y <= cube.x) return vec2(0.0);
    vec2 interval = vaporBoundsInterval(origin, direction, auramVaporMinimum, auramVaporMaximum, cube.y);
    float first = max(interval.x, cube.x);
    #ifdef VAPOR_BEHIND_TRANSLUCENT
        first = max(first, vaporTranslucentStart);
    #endif
    float last = interval.y;
    #ifdef AURAM_VAPOR_RADIUS
        // The simulation can only supply density inside its own field.
        vec3 offset = origin - auramVaporCenter;
        float speed2 = dot(direction, direction);
        float along = dot(offset, direction);
        float discriminant = along * along + speed2 * (vaporRadius * vaporRadius - dot(offset, offset));
        if (discriminant <= 0.0) return vec2(0.0);
        float halfLength = sqrt(discriminant);
        first = max(first, (-along - halfLength) / speed2);
        last = min(last, (-along + halfLength) / speed2);
    #endif
    return last > first ? vec2(first, last) : vec2(0.0);
}

float vaporDetailNoise(vec3 position) {
    position = mod(position, vaporNoisePeriod);
    vec3 cell = floor(position);
    vec3 blend = fract(position);
    blend = blend * blend * (3.0 - 2.0 * blend);
    // Use two slices of the existing noise texture as a repeatable 3D field.
    const vec2 sliceOffset = vec2(37.0, 151.0);
    vec2 base = mod(cell.xz + cell.y * sliceOffset, vaporNoisePeriod) + blend.xz + 0.5;
    float lower = textureLod(noisetex, base / vaporNoisePeriod, 0.0).a;
    float upper = textureLod(noisetex, (base + sliceOffset) / vaporNoisePeriod, 0.0).a;
    return mix(lower, upper, blend.y);
}

vec3 vaporNoiseOrigin(vec3 scale, out vec3 frequency) {
    // Multiply the whole-block camera coordinate in fixed point. The texture
    // repeats every 512 cells, so masking preserves the phase even at the world border.
    uvec3 fixedScale = uvec3(scale * vaporNoiseFixedPoint + 0.5);
    frequency = vec3(fixedScale) / vaporNoiseFixedPoint;
    uvec3 phase = (uvec3(cameraPositionInt) * fixedScale) & uvec3(vaporNoiseMask);
    return vec3(phase) / vaporNoiseFixedPoint + cameraPositionFract * frequency;
}

float vaporDensity(vec3 playerPos, vec3 bendPosition, vec3 detailPosition) {
    vec3 local = playerPos - auramVaporOrigin;
    ivec3 size = textureSize(auramVaporVolume, 0);
    ivec3 cell = clamp(ivec3(floor(local)), ivec3(0), size - 1);
    // Full collision cells mask the trilinear filter at glass and walls.
    if (texelFetch(auramVaporVolume, cell, 0).b < 0.5) return 0.0;
    vec2 densityFrames = textureLod(auramVaporVolume, local / vec3(size), 0.0).rg;
    float density = mix(densityFrames.x, densityFrames.y, auramVaporBlend);
    if (density <= 0.001) return 0.0;
    density *= vaporEdgeFade(playerPos - vaporCenter());
    #ifdef AURAM_VAPOR_RADIUS
        // Fade in world space so the last few blocks stay smooth when the voxel grid moves.
        vec3 offset = playerPos - auramVaporCenter;
        const float fadeStart = vaporRadius - 4.0;
        density *= 1.0 - smoothstep(fadeStart * fadeStart, vaporRadius * vaporRadius, dot(offset, offset));
    #endif
    if (density <= 0.001) return 0.0;

    float bend = vaporDetailNoise(bendPosition) - 0.5;
    float detail = vaporDetailNoise(detailPosition + bend * vec3(2.4, 0.75, -2.0));
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
    float last = min(stop, interval.y);
    if (last <= first) return VaporScattering(vec3(0.0), vec3(0.0), 1.0);

    #if VAPOR_MAX_OPACITY < 100
        float minimumTransmission = (1.0 - VAPOR_MAX_OPACITY / 100.0) / max(rayTransmission, 1e-6);
        if (minimumTransmission >= 1.0) return VaporScattering(vec3(0.0), vec3(0.0), 1.0);
    #endif
    // March on world-aligned planes along the dominant axis. Use the same
    // planes for every fog segment and a coarser subset when the full ray needs it.
    vec3 speed = abs(direction);
    int axis = speed.x >= speed.y && speed.x >= speed.z ? 0 : (speed.y >= speed.z ? 1 : 2);
    float axisSpeed = speed[axis];
    const float baseUnits = floor(VAPOR_SAMPLE_SPACING * 256.0 + 0.5);
    float minUnits = (interval.y - interval.x) * axisSpeed * 256.0 / float(VAPOR_MAX_SAMPLES - 1);
    int stepUnits = int(baseUnits) << int(ceil(log2(max(minUnits / baseUnits, 1.0))));
    float axisSpacing = float(stepUnits) / 256.0;
    float stride = axisSpacing / axisSpeed;
    int cameraBlock = cameraPositionInt[axis];
    float cameraAxis = float(cameraBlock - (cameraBlock / stepUnits) * stepUnits)
                     + cameraPositionFract[axis] + origin[axis];
    float phase = mod(sign(direction[axis]) * cameraAxis, axisSpacing) / axisSpeed;
    float sampleCenter = floor((first + phase) / stride + 0.5) * stride - phase;
    vec3 bendFrequency, detailFrequency;
    vec3 bendOrigin = vaporNoiseOrigin(vaporBendScale, bendFrequency);
    vec3 detailOrigin = vaporNoiseOrigin(vaporDetailScale, detailFrequency);
    bendOrigin.y -= frameTimeCounter * 0.8 * bendFrequency.y;
    detailOrigin.y -= frameTimeCounter * 0.8 * detailFrequency.y;
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
    for (int i = 0; i < VAPOR_MAX_SAMPLES; i++, sampleCenter += stride) {
        float cellStart = sampleCenter - 0.5 * stride;
        if (cellStart >= last) break;
        float cellEnd = sampleCenter + 0.5 * stride;
        float sampleLength = min(last, cellEnd) - max(first, cellStart);
        if (sampleLength <= 0.0) continue;
        // Keep a partial cell's sample inside the visible volume at either end.
        float sampleDistance = sampleCenter < interval.x || sampleCenter >= interval.y
                             ? 0.5 * (max(interval.x, cellStart) + min(interval.y, cellEnd))
                             : sampleCenter;
        vec3 position = origin + direction * sampleDistance;
        float density = vaporDensity(position, bendOrigin + position * bendFrequency, detailOrigin + position * detailFrequency);
        if (density <= 0.001) continue;
        float segmentTransmission = exp(-VAPOR_EXTINCTION * density * sampleLength);
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
