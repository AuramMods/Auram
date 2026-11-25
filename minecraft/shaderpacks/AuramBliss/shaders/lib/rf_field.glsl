#ifndef AURAM_RF_FIELD_GLSL
#define AURAM_RF_FIELD_GLSL

uniform usampler3D auramRfVolume;
uniform bool auramRfActive;
uniform vec3 auramRfOrigin;
uniform vec3 auramRfMinimum;
uniform vec3 auramRfMaximum;
uniform vec3 auramRfPhaseOrigin;

const float rfTau = 6.2831853;
const float rfPulseSpacing = 2.0;
const float rfTrailLength = 1.72;

// Texels contain logarithmic rates, with 31 codes per decade. This response is
// intentionally editable in the pack so shader reloads can tune the visible range.
float rfBrightness(uint rate) {
    float decades = float(rate) / 31.0;
    float low = clamp(decades * 0.25, 0.0, 1.0);
    return 0.14 * low * low * low + 0.86 * smoothstep(4.0, 8.0, decades);
}

vec2 rfWanderLattice(vec2 grid, float seed) {
    grid = mod(grid, vec2(128.0, 16.0));
    vec3 hash = fract(grid.xyx * vec3(0.1031, 0.1030, 0.0973) + seed);
    hash += dot(hash, hash.yzx + 33.33);
    return 2.0 * fract((hash.xx + hash.yz) * hash.zy) - 1.0;
}

float rfTravelPhase() {
    const float originalSpeed = 3.45;
    const float noiseInterval = 0.25;
    float slowTime = frameTimeCounter / 3.0;
    float clock = slowTime / noiseInterval;
    float progress = fract(clock);
    float noise = rfWanderLattice(vec2(floor(clock), 5.0), 0.347).x;
    // This displacement and its slope meet at zero across noise intervals.
    // The derivative of the normalized bump ranges from -1 to +1.
    float bump = progress * (1.0 - progress);
    float jitter = 2.0 * noise * noiseInterval * 5.1961524 * bump * bump;
    return mod(originalSpeed * (5.0 * slowTime + jitter), rfPulseSpacing);
}

float rfCircuitPhase(uint clock, float fallback) {
    if (clock == 0u) return fallback;
    // The client puts one phase per connected run in the slice beyond the RF field.
    uint index = clock - 1u;
    return uintBitsToFloat(texelFetch(auramRfVolume, ivec3(index % 64u, index / 64u, 64), 0).r);
}

float rfPhaseShift(vec3 world) {
    // A moving speed field compresses and stretches the band's travel coordinate.
    // Both sides of a cable face evaluate it at the same world position.
    vec2 angle = vec2(dot(world, vec3(43.0, 29.0, 53.0)),
                      dot(world, vec3(31.0, -47.0, 38.0))) * (rfTau / 256.0);
    angle += (rfTau * frameTimeCounter / 3600.0) * vec2(-1604.0, 974.0);
    return dot(sin(angle), vec2(0.14, 0.09));
}

float rfCenterPhase(ivec3 block) {
    // The two sides of each shared cable face meet at the same phase modulo two.
    return mod(dot(vec3(block) + auramRfPhaseOrigin, vec3(1.0)), rfPulseSpacing);
}

float rfEnvelope(float age) {
    return smoothstep(0.0, 0.21, age) * (1.0 - smoothstep(0.42, 1.0, age));
}

vec3 rfBandTint(vec3 world, float age, float rate) {
    float decades = rate / 31.0;
    vec3 blue = mix(vec3(0.045, 0.20, 0.80), vec3(0.19, 0.57, 1.0), smoothstep(3.8, 6.8, decades));
    float violetRange = smoothstep(5.85, 7.8, decades);
    float wave = 0.5 + 0.5 * cos((5.0 / 3.0) * frameTimeCounter - age * 3.1
                                  + rfTau * dot(world, vec3(3.0, 1.0, 5.0)) / 32.0);
    vec3 tint = mix(blue, vec3(0.40, 0.19, 1.0), 0.9 * violetRange * smoothstep(0.20, 0.96, wave));
    #if RF_COLOR_SATURATION != 100
        float neutral = dot(tint, vec3(0.2126, 0.7152, 0.0722));
        tint = max(mix(vec3(neutral), tint, RF_COLOR_SATURATION / 100.0), vec3(0.0));
    #endif
    return tint;
}

// LPV positions are relative to the floored camera; the RF origin is relative
// to the continuous camera position. Converting both to voxel indices aligns them.
vec3 rfVoxelEmission(ivec3 voxel, int side, vec3 camera) {
    if (!auramRfActive) return vec3(0.0);
    vec3 offset = auramRfOrigin + fract(camera);
    ivec3 block = voxel - ivec3(side / 2) - ivec3(round(offset));
    if (any(lessThan(vec3(block), auramRfMinimum)) || any(greaterThanEqual(vec3(block), auramRfMaximum))) {
        return vec3(0.0);
    }
    uvec3 rates = texelFetch(auramRfVolume, block, 0).rgb;
    if (rates == uvec3(0u)) return vec3(0.0);
    uvec3 peak = max(rates & 255u, (rates >> 8u) & 255u);
    peak = max(peak, (rates >> 16u) & 255u);
    peak = max(peak, rates >> 24u);
    uint rate = max(peak.x, max(peak.y, peak.z));

    // A powered cable lights the whole run. The stored rate is already logarithmic.
    float decades = float(rate) / 31.0;
    float brightness = 0.02 * min(decades / 3.5, 1.0) + 0.98 * clamp((decades - 3.5) / 4.5, 0.0, 1.0);
    vec3 world = vec3(block) + 0.5 + auramRfPhaseOrigin;
    return rfBandTint(world, 0.5, float(rate)) * (1400.0 * RF_STREAK_INTENSITY * brightness * RF_FLOODFILL_BRIGHTNESS);
}
#endif
