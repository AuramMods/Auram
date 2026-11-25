#ifdef TWILIGHT_SHADER
    const float smokeFloor = float(TWILIGHT_SMOKE_FLOOR);
    #include "/lib/twilight_smoke.glsl"
#else
    const float smokeFloor = float(ATUM_SMOKE_FLOOR);
    #include "/lib/atum_smoke.glsl"
#endif

vec2 smokeIntervalInLayer(float originY, float directionY, vec2 cube) {
    if (abs(directionY) < 1e-6) {
        return originY > smokeFloor && originY < smokeTop ? cube : vec2(0.0);
    }

    vec2 intersections = (vec2(smokeFloor, smokeTop) - originY) / directionY;
    return vec2(max(min(intersections.x, intersections.y), cube.x), min(max(intersections.x, intersections.y), cube.y));
}

float smokeOpticalDepth(vec3 origin, vec3 direction, vec3 cameraFromCenter, vec2 interval, float stepStart, float stepEnd, float dither) {
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
        float rangeFade = vaporEdgeFade(cameraFromCenter + direction * distance);
        density += smokeDensity(origin + direction * distance) * rangeFade;
    }
    return density * stride;
}
