vec3 tintAtumAtmosphere(vec3 color) {
    const vec3 luminanceWeights = vec3(0.2126, 0.7152, 0.0722);
    const vec3 tint = vec3(ATUM_ATMOSPHERE_R, ATUM_ATMOSPHERE_G, ATUM_ATMOSPHERE_B);
    vec3 tinted = tint * (dot(color, luminanceWeights) / max(dot(tint, luminanceWeights), 0.001));
    return mix(color, tinted, ATUM_ATMOSPHERE_STRENGTH);
}
