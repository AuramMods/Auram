#ifdef AURAM_BLISS_ASTRAL_SKY
uniform float auramAstralSolarProgress;
uniform float auramAstralLunarCoverage;

float blissSolarVisibility() {
    return 0.05 + 0.95 * abs(auramAstralSolarProgress * 2.0 - 1.0);
}

vec3 blissLunarTint() {
    return vec3(1.0, vec2(1.0 - 0.6 * auramAstralLunarCoverage));
}

vec3 blissEclipseDisk(vec3 color, vec3 ray, vec3 sunDirection, vec3 sunlight) {
    if (auramAstralSolarProgress == 0.0) return color;
    const float diskRadius = 0.0093084168595 * 2.4;
    float transit = auramAstralSolarProgress * 2.0 - 1.0;
    vec3 tangent = abs(sunDirection.y) < 0.999
        ? normalize(vec3(sunDirection.z, 0.0, -sunDirection.x)) : vec3(1.0, 0.0, 0.0);
    float offset = transit * 2.0 * diskRadius;
    vec3 occultingDirection = cos(offset) * sunDirection + sin(offset) * tangent;
    float visibleDisk = 1.0 - smoothstep(cos(diskRadius * 1.006), cos(diskRadius * 0.994), dot(ray, occultingDirection));
    float radial = acos(clamp(dot(ray, sunDirection), -1.0, 1.0)) / diskRadius;
    float corona = smoothstep(0.97, 1.03, radial) * exp(-14.0 * max(radial - 1.03, 0.0));
    return color * visibleDisk + 5.0 * sunlight * corona * pow(1.0 - abs(transit), 4.0);
}
#endif
