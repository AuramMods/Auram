vec4 blissUnpackMaterial(uint packedValue) {
    uvec4 bytes = (uvec4(packedValue) >> uvec4(0u, 8u, 16u, 24u)) & 255u;
    return vec4(bytes) / 255.0;
}
