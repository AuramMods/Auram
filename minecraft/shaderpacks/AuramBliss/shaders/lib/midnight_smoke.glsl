#include "/lib/nether_smoke.glsl"

vec2 midnightSmokeOpticalDepth(vec3 origin, vec3 direction, vec3 cameraFromCenter, vec2 interval, float start, float end, float dither) {
    start = max(start, interval.x);
    float segmentLength = min(end, interval.y) - start;
    if (segmentLength <= 0.0) return vec2(0.0);

    int samples = min(6, int(ceil(segmentLength / 12.0)));
    float stride = segmentLength / float(samples);
    vec2 opticalDepth = vec2(0.0);
    for (int i = 0; i < samples; i++) {
        float distance = start + (float(i) + dither) * stride;
        vec3 samplePos = origin + direction * distance;
        vec2 sampleDepth = vec2(0.0);
        // The shared Nether field is anchored at Y=31.
        samplePos.y += 31.0 - float(MIDNIGHT_SMOKE_FLOOR);
        if (MIDNIGHT_PLUME_DENSITY > 0.0 && samplePos.y < 100.0) {
            float field = netherSmokeField(samplePos, MIDNIGHT_SMOKE_SCALE, frameTimeCounter * (10.0 * MIDNIGHT_SMOKE_SPEED));
            float plume = netherSmokePlumeDensity(field, samplePos.y) * MIDNIGHT_PLUME_DENSITY;
            sampleDepth += plume * vec2(1.0, netherSmokePlumeLight(field));
        }
        float upper = netherSmokeUpperDensity(samplePos.y) * MIDNIGHT_CEILING_SMOKE_DENSITY;
        sampleDepth += upper * vec2(1.0, 0.45);
        opticalDepth += sampleDepth * vaporEdgeFade(cameraFromCenter + direction * distance);
    }
    return opticalDepth * stride;
}
