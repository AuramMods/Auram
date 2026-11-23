#include "/lib/nether_smoke.glsl"

vec2 midnightSmokeOpticalDepth(vec3 origin, vec3 direction, float start, float end, float dither) {
    const float smokeRange = 192.0;
    float segmentLength = min(end, smokeRange) - start;
    if (segmentLength <= 0.0) return vec2(0.0);

    int samples = min(6, int(ceil(segmentLength / 12.0)));
    float stride = segmentLength / float(samples);
    vec2 opticalDepth = vec2(0.0);
    for (int i = 0; i < samples; i++) {
        float distance = start + (float(i) + dither) * stride;
        vec3 samplePos = origin + direction * distance;
        // The shared Nether field is anchored at Y=31.
        samplePos.y += 31.0 - float(MIDNIGHT_SMOKE_FLOOR);
        if (MIDNIGHT_PLUME_DENSITY > 0.0 && samplePos.y < 100.0) {
            float field = netherSmokeField(samplePos, MIDNIGHT_SMOKE_SCALE, frameTimeCounter * (10.0 * MIDNIGHT_SMOKE_SPEED));
            float plume = netherSmokePlumeDensity(field, samplePos.y, distance / smokeRange) * MIDNIGHT_PLUME_DENSITY;
            opticalDepth += plume * vec2(1.0, netherSmokePlumeLight(field));
        }
        float upper = netherSmokeUpperDensity(samplePos.y) * MIDNIGHT_CEILING_SMOKE_DENSITY;
        upper *= 1.0 - smoothstep(128.0, smokeRange, distance);
        opticalDepth += upper * vec2(1.0, 0.45);
    }
    return opticalDepth * stride;
}
