#ifndef INCLUDE_SKY_ASTRAL_ECLIPSE
#define INCLUDE_SKY_ASTRAL_ECLIPSE

uniform float auramAstralSolarProgress;
uniform float auramAstralLunarCoverage;

float astral_solar_visibility() {
    return 0.05 + 0.95 * abs(2.0 * auramAstralSolarProgress - 1.0);
}

vec3 astral_lunar_tint() {
    return vec3(1.0, vec2(1.0 - 0.6 * auramAstralLunarCoverage));
}

#endif
