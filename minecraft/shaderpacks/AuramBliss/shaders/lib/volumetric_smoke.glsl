const float smokeRange = 768.0;

#ifdef TWILIGHT_SHADER
    const float smokeFloor = float(TWILIGHT_SMOKE_FLOOR);
    #include "/lib/twilight_smoke.glsl"
#else
    const float smokeFloor = float(ATUM_SMOKE_FLOOR);
    #include "/lib/atum_smoke.glsl"
#endif

vec2 smokeIntervalInLayer(float originY, float directionY, float rayEnd) {
    float traceEnd = min(rayEnd, smokeRange);
    if (abs(directionY) < 1e-6) {
        return originY > smokeFloor && originY < smokeTop ? vec2(0.0, traceEnd) : vec2(0.0);
    }

    vec2 intersections = (vec2(smokeFloor, smokeTop) - originY) / directionY;
    return vec2(max(min(intersections.x, intersections.y), 0.0), min(max(intersections.x, intersections.y), traceEnd));
}

float smokeOpticalDepth(vec3 origin, vec3 direction, vec2 interval, float stepStart, float stepEnd, float dither) {
    float start = max(stepStart, interval.x);
    float stop = min(stepEnd, interval.y);
    float lengthInSmoke = stop - start;
    if (lengthInSmoke <= 0.0) return 0.0;

    // Resolve wide fog steps without spending extra samples outside the smoke layer.
    int samples = min(smokeMaxSamples, int(ceil(lengthInSmoke / smokeSampleSpacing)));
    float stride = lengthInSmoke / float(samples);
    float density = 0.0;
    for (int i = 0; i < samples; i++) {
        float distance = start + (float(i) + dither) * stride;
        float rangeFade = 1.0 - smoothstep(384.0, smokeRange, distance);
        density += smokeDensity(origin + direction * distance) * rangeFade;
    }
    return density * stride;
}
