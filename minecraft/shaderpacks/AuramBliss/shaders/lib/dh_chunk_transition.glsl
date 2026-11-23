// Vanilla chunk range is horizontal, also when looking up or down a cliff.
float dhChunkDistance(vec3 cameraRelativePosition) {
    return length(cameraRelativePosition.xz);
}

float dhOverlapEnd(float vanillaFar) {
    float overlapEnd = max(vanillaFar - 16.0 * DH_OVERDRAW_CHUNKS, min(vanillaFar, 16.0));
    #if OVERDRAW_MAX_DISTANCE > 0
        overlapEnd = min(overlapEnd, float(OVERDRAW_MAX_DISTANCE));
    #endif
    return overlapEnd;
}

float dhFadeEnd(float overlapEnd, float vanillaFar) {
    return min(overlapEnd + float(DH_OVERDRAW_FADE_LENGTH), vanillaFar);
}

float dhFadeCoverage(float startDistance, float endDistance, float distance) {
    if (endDistance <= startDistance) return step(endDistance, distance);
    return smoothstep(startDistance, endDistance, distance);
}

float dhTransitionDither(int frameCounter) {
    vec2 pixel = floor(gl_FragCoord.xy);
    #ifdef TAA
        pixel += float(frameCounter % 64) * vec2(0.75487766, 0.56984029);
    #endif
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

bool dhKeepTranslucentLod(float distance, float vanillaFar, int frameCounter) {
    float overlapEnd = dhOverlapEnd(vanillaFar);
    float fadeStart = max(overlapEnd - float(DH_OVERDRAW_FADE_LENGTH), 0.0);
    // Water uses the whole overlap so the two representations never stack at the same pixel.
    return dhTransitionDither(frameCounter) < dhFadeCoverage(fadeStart, dhFadeEnd(overlapEnd, vanillaFar), distance);
}

bool dhFadeVanillaTerrain(float distance, float vanillaFar, int frameCounter) {
    float overlapEnd = dhOverlapEnd(vanillaFar);
    return dhTransitionDither(frameCounter) < dhFadeCoverage(overlapEnd, dhFadeEnd(overlapEnd, vanillaFar), distance);
}

float dhViewDepth(float depth, mat4 projectionInverse) {
    vec2 view = projectionInverse[2].zw * (depth * 2.0 - 1.0) + projectionInverse[3].zw;
    return -view.x / view.y;
}

bool dhMatchesSurface(float lodDepth, mat4 dhProjectionInverse, vec3 vanillaViewPosition) {
    if (lodDepth <= 0.0 || lodDepth >= 1.0) return false;
    // Keep vanilla pixels when the only LOD is a different surface farther along the ray.
    float vanillaDepth = -vanillaViewPosition.z;
    float oneChunkInView = 16.0 * vanillaDepth / max(length(vanillaViewPosition), 1.0);
    return abs(dhViewDepth(lodDepth, dhProjectionInverse) - vanillaDepth) <= oneChunkInView;
}
