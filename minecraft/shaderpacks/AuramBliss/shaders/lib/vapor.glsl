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
#ifdef VAPOR_BEHIND_TRANSLUCENT
float vaporTranslucentStart = 0.0;
#elif defined EXCLUDE_WRITE_TO_LUT
float vaporOpaqueDistance = -2.0;
#endif

// Keep the finest world grid near the eye. More detail extends that grid
// farther before the ray moves to wider cells.
const float vaporGridBandWidth = max(8.0, VAPOR_SAMPLE_SPACING * float(VAPOR_MAX_SAMPLES));

struct VaporRay {
    vec2 visible;
};

VaporRay vaporRayInterval(vec3 origin, vec3 direction, float surfaceDistance) {
    if (!auramVaporActive) return VaporRay(vec2(0.0));
    // Configured range is an axis-aligned radius. The backend may be older and provide less data.
    #ifdef AURAM_VAPOR_RADIUS
        const vec3 halfExtent = vec3(float(min(VAPOR_RENDER_RANGE, AURAM_VAPOR_RADIUS)));
    #else
        const vec3 halfExtent = vec3(float(VAPOR_RENDER_RANGE));
    #endif
    vec3 center = vaporCenter();
    float rayEnd = length(abs(origin - center) + halfExtent);
    vec2 cube = vaporBoundsInterval(origin, direction, center - halfExtent, center + halfExtent, rayEnd);
    if (cube.y <= cube.x) return VaporRay(vec2(0.0));
    vec2 interval = vaporBoundsInterval(origin, direction, auramVaporMinimum, auramVaporMaximum, cube.y);
    float first = max(interval.x, cube.x);
    float last = interval.y;
    #ifdef VAPOR_BEHIND_TRANSLUCENT
        first = max(first, vaporTranslucentStart);
    #elif defined EXCLUDE_WRITE_TO_LUT
        if (vaporOpaqueDistance >= -1.0) surfaceDistance = vaporOpaqueDistance;
    #endif
    if (surfaceDistance >= 0.0) last = min(last, surfaceDistance);
    return VaporRay(last > first ? vec2(first, last) : vec2(0.0));
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

vec3 vaporWorldPhase(ivec3 worldOrigin, vec3 scale, out vec3 frequency) {
    // Multiply whole-block coordinates in fixed point. Periodic sampling
    // needs only the low bits, even near the world border.
    uvec3 fixedScale = uvec3(scale * vaporNoiseFixedPoint + 0.5);
    frequency = vec3(fixedScale) / vaporNoiseFixedPoint;
    uvec3 phase = (uvec3(worldOrigin) * fixedScale) & uvec3(vaporNoiseMask);
    return vec3(phase) / vaporNoiseFixedPoint;
}

struct VaporGrid {
    float width;
    float end;
    float cellStart;
    vec3 nextPlanes;
    vec3 planeSpacing;
};

VaporGrid vaporWorldGrid(ivec3 worldOrigin, vec3 localOrigin, vec3 direction,
                        float first, float bandStart, float bandWidth) {
    vec3 frequency;
    float gridScale = bandWidth / vaporGridBandWidth;
    // Keep thin distant plumes sampleable after the adaptive bands widen.
    float planeSpacing = min(2.0 * VAPOR_SAMPLE_SPACING * gridScale, max(2.0, 2.0 * VAPOR_SAMPLE_SPACING));
    vec3 gridOrigin = fract(vaporWorldPhase(worldOrigin, vec3(1.0 / planeSpacing), frequency));
    vec3 raySign = mix(vec3(-1.0), vec3(1.0), greaterThanEqual(direction, vec3(0.0)));
    vec3 entry = localOrigin + direction * first;
    vec3 phase = fract(raySign * (gridOrigin + entry * frequency));
    vec3 speed = abs(direction) * frequency;
    bvec3 crosses = greaterThan(speed, vec3(1e-6));
    vec3 spacing = 1.0 / max(speed, vec3(1e-6));
    vec3 next = mix(vec3(1e6), first + (1.0 - phase) * spacing, crosses);
    vec3 previous = mix(vec3(-1e6), first - phase * spacing, crosses);
    float cellStart = max(bandStart, max(previous.x, max(previous.y, previous.z)));
    return VaporGrid(bandWidth, bandStart + bandWidth, cellStart, next, spacing);
}

float vaporDensity(vec3 local, vec3 bendPosition, vec3 detailPosition) {
    ivec3 size = textureSize(auramVaporVolume, 0);
    ivec3 cell = clamp(ivec3(floor(local)), ivec3(0), size - 1);
    // Full collision cells mask the trilinear filter at glass and walls.
    if (texelFetch(auramVaporVolume, cell, 0).b < 0.5) return 0.0;
    vec2 densityFrames = textureLod(auramVaporVolume, local / vec3(size), 0.0).rg;
    float density = mix(densityFrames.x, densityFrames.y, auramVaporBlend);
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

VaporScattering traceVapor(vec3 origin, vec3 direction, VaporRay ray, float start, float stop, vec3 ambient, inout float rayTransmission) {
    vec2 interval = ray.visible;
    float first = max(start, interval.x);
    float last = min(stop, interval.y);
    if (last <= first) return VaporScattering(vec3(0.0), vec3(0.0), 1.0);

    #if VAPOR_MAX_OPACITY < 100
        float minimumTransmission = (1.0 - VAPOR_MAX_OPACITY / 100.0) / max(rayTransmission, 1e-6);
        if (minimumTransmission >= 1.0) return VaporScattering(vec3(0.0), vec3(0.0), 1.0);
    #endif
    // AuramCore publishes an integer world voxel origin relative to the camera.
    ivec3 worldOrigin = cameraPositionInt + ivec3(round(cameraPositionFract + auramVaporOrigin));
    vec3 localOrigin = origin - auramVaporOrigin;
    float bandWidth = vaporGridBandWidth * exp2(floor(log2(1.0 + first / vaporGridBandWidth)));
    VaporGrid grid = vaporWorldGrid(worldOrigin, localOrigin, direction, first, bandWidth - vaporGridBandWidth, bandWidth);
    vec3 bendFrequency, detailFrequency;
    vec3 bendOrigin = vaporWorldPhase(worldOrigin, vaporBendScale, bendFrequency);
    vec3 detailOrigin = vaporWorldPhase(worldOrigin, vaporDetailScale, detailFrequency);
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
    while (grid.cellStart < last) {
        float cellStart = grid.cellStart;
        float cellEnd = min(grid.end, min(grid.nextPlanes.x, min(grid.nextPlanes.y, grid.nextPlanes.z)));
        if (cellEnd == grid.end && cellEnd < last) {
            grid = vaporWorldGrid(worldOrigin, localOrigin, direction, cellEnd, cellEnd, grid.width * 2.0);
        } else {
            // Advance all tied planes so edges and corners cannot stall the ray.
            grid.nextPlanes += mix(vec3(0.0), grid.planeSpacing, lessThanEqual(grid.nextPlanes, vec3(cellEnd)));
            grid.cellStart = cellEnd;
        }
        float cellNear = max(interval.x, cellStart);
        float cellFar = min(interval.y, cellEnd);
        float cellLength = cellFar - cellNear;
        if (cellLength <= 0.0) continue;

        // Integrate long chords at two Gauss points. A midpoint can miss
        // curved lighting gradients when looking along the face of a cell.
        // Use the same points when a fog step splits a cell.
        int samples = cellLength > 0.25 ? 2 : 1;
        float midpoint = 0.5 * (cellNear + cellFar);
        for (int i = 0; i < samples; i++) {
            float sampleNear = samples == 1 || i == 0 ? cellNear : midpoint;
            float sampleFar = samples == 1 || i == 1 ? cellFar : midpoint;
            float sampleLength = min(last, sampleFar) - max(first, sampleNear);
            if (sampleLength <= 0.0) continue;
            float sampleDistance = samples == 1 ? midpoint
                : midpoint + (i == 0 ? -0.28867513 : 0.28867513) * cellLength;
            vec3 position = origin + direction * sampleDistance;
            vec3 local = localOrigin + direction * sampleDistance;
            float density = vaporDensity(local, bendOrigin + local * bendFrequency, detailOrigin + local * detailFrequency);
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
