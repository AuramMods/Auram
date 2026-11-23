const vec3 midnightPurple = vec3(0.46, 0.12, 0.84);

vec3 midnightSkyRadiance(vec3 direction) {
    float horizon = exp(-4.0 * abs(direction.y));
    float belowHorizon = mix(0.28, 1.0, smoothstep(-0.4, 0.03, direction.y));
    return midnightPurple * ((0.009 + 0.009 * horizon) * belowHorizon * MIDNIGHT_SKY_BRIGHTNESS);
}
