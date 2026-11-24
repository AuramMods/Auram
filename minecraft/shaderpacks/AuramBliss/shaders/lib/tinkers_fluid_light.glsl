bool replacesFluidLightFallback(uint materialId, vec3 fluidLight) {
    // Oculus encodes otherwise unmapped luminous blocks as white light blocks.
    return materialId >= uint(BLOCK_LIGHT_1) && materialId <= uint(BLOCK_LIGHT_15)
            && any(greaterThan(fluidLight, vec3(0.0)));
}

uniform sampler3D auramTinkersFluidLight;
uniform bool auramTinkersFluidLightActive;
uniform vec3 auramTinkersFluidLightOrigin;
uniform vec3 auramTinkersFluidLightMinimum;
uniform vec3 auramTinkersFluidLightMaximum;

vec3 tinkersFluidEmission(ivec3 voxel, int side, vec3 camera) {
    if (!auramTinkersFluidLightActive) return vec3(0.0);
    vec3 playerOffset = vec3(voxel - side / 2) - fract(camera);
    vec3 cell = floor(playerOffset + 0.5 - auramTinkersFluidLightOrigin);
    if (any(lessThan(cell, auramTinkersFluidLightMinimum))
            || any(greaterThanEqual(cell, auramTinkersFluidLightMaximum))) return vec3(0.0);

    vec4 source = texelFetch(auramTinkersFluidLight, ivec3(cell), 0);
    vec3 color = srgbToLinear(source.rgb);
    float peak = max(color.r, max(color.g, color.b));
    return color * (96.0 * source.a / max(peak, 0.125));
}
