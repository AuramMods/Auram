#include "/lib/settings.glsl"
#include "/lib/Shadow_Params.glsl"

vec4 voxyShadowPosition(vec4 position) {
    return blissShadowClipPosition(position);
}

// Return conservative XY bounds in the same warped clip space as the casters.
vec4 voxyShadowBounds(vec4 bounds) {
    #ifdef DISTORT_SHADOWMAP
        vec2 nearest = max(max(bounds.xy, -bounds.zw), vec2(0.0));
        vec2 furthest = max(abs(bounds.xy), abs(bounds.zw));
        float narrow = calcDistort(furthest);
        float wide = calcDistort(nearest);
        return vec4(min(bounds.xy * narrow, bounds.xy * wide),
                    max(bounds.zw * narrow, bounds.zw * wide));
    #else
        return bounds;
    #endif
}

float voxyShadowReceiverEdge(float edge) {
    #ifdef DISTORT_SHADOWMAP
        // For a negative half-plane edge, the strongest warp occurs at its closest
        // point to the origin. A nonnegative edge can conservatively extend to zero.
        edge = min(edge, 0.0);
        edge *= calcDistort(vec2(edge, 0.0));
    #endif
    // Include the widest blocker/filter kernels and hardware filtering.
    return edge - max(128.0, 4.0 * Max_Shadow_Filter_Radius) / float(shadowMapResolution);
}
