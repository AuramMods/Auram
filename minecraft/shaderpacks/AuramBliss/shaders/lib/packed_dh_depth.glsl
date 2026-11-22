// Colortex12 contains three raw depth channels in the compatibility path.
// Preserve Bliss's binary16 mixed-depth encoding in the fourth channel.
float blissEncodeMixedDepth(float depth) {
    float encoded = depth < 1.0 ? depth * depth * 65000.0 : 65000.0;
    if (encoded < exp2(-14.0)) {
        return roundEven(encoded * exp2(24.0)) * exp2(-24.0);
    }
    uint bits = floatBitsToUint(encoded);
    return uintBitsToFloat((bits + 0xfffu + ((bits >> 13u) & 1u)) & ~0x1fffu);
}
