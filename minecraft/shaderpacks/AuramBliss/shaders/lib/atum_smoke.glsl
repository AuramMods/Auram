const float smokeTop = clamp(float(ATUM_SMOKE_HEIGHT), 80.0, 128.0);
const int smokeMaxSamples = 3;
const float smokeSampleSpacing = 18.0;

float smokeDensity(vec3 pos) {
    float height = pos.y - smokeFloor;
    float span = smokeTop - smokeFloor;
    float rise = clamp(height / span, 0.0, 1.0);

    // Keep each column rooted while bending and eroding its upper part.
    float drift = frameTimeCounter * ATUM_SMOKE_WIND;
    vec3 noisePos = vec3(pos.x * 3.0, height * 4.0, pos.z * 3.0) + vec3(4.0, -18.0, -3.0) * drift;
    float erosion = densityAtPosFog(noisePos);
    vec2 bend = vec2(24.0, -14.0) * (rise * rise) + vec2(12.0, 8.0) * (erosion - 0.5) * rise;
    float column = texture2DLod(noisetex, (pos.xz + bend) / ATUM_SMOKE_SCALE, 0.0).b;

    float threshold = 1.0 - ATUM_SMOKE_COVERAGE;
    float core = smoothstep(threshold, threshold + 0.16, column + (erosion - 0.5) * 0.16);
    float peak = mix(12.0, span, smoothstep(threshold, threshold + 0.29, column));
    float heightMask = smoothstep(0.0, 4.0, height) * (1.0 - smoothstep(peak * 0.64, peak, height));
    return core * heightMask * mix(0.45, 1.0, erosion) * ATUM_SMOKE_DENSITY;
}
