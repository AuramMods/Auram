const float atumSmokeFloor = 64.0;
const float atumSmokeTop = clamp(float(ATUM_SMOKE_HEIGHT), 80.0, 128.0);
const float atumSmokeRange = 768.0;

vec2 atumSmokeInterval(float originY, float directionY, float rayEnd) {
    float traceEnd = min(rayEnd, atumSmokeRange);
    if (abs(directionY) < 1e-6) {
        return originY > atumSmokeFloor && originY < atumSmokeTop ? vec2(0.0, traceEnd) : vec2(0.0);
    }

    vec2 intersections = (vec2(atumSmokeFloor, atumSmokeTop) - originY) / directionY;
    return vec2(max(min(intersections.x, intersections.y), 0.0), min(max(intersections.x, intersections.y), traceEnd));
}

float atumSmokeDensity(vec3 pos) {
    float height = pos.y - atumSmokeFloor;
    float span = atumSmokeTop - atumSmokeFloor;
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

float atumSmokeOpticalDepth(vec3 origin, vec3 direction, vec2 interval, float stepStart, float stepEnd, float dither) {
    float start = max(stepStart, interval.x);
    float stop = min(stepEnd, interval.y);
    float lengthInSmoke = stop - start;
    if (lengthInSmoke <= 0.0) return 0.0;

    // Resolve wide fog steps without spending extra samples outside the smoke layer.
    int samples = min(3, int(ceil(lengthInSmoke / 18.0)));
    float stride = lengthInSmoke / float(samples);
    float density = 0.0;
    for (int i = 0; i < samples; i++) {
        float distance = start + (float(i) + dither) * stride;
        float rangeFade = 1.0 - smoothstep(384.0, atumSmokeRange, distance);
        density += atumSmokeDensity(origin + direction * distance) * rangeFade;
    }
    return density * stride;
}
