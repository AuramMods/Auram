const vec3 midnightTint = vec3(MIDNIGHT_SKY_R, MIDNIGHT_SKY_G, MIDNIGHT_SKY_B);

vec3 midnightSkyRadiance(vec3 direction) {
    float horizon = exp(-4.0 * abs(direction.y));
    float belowHorizon = mix(0.28, 1.0, smoothstep(-0.4, 0.03, direction.y));
    return midnightTint * ((0.009 + 0.009 * horizon) * belowHorizon * MIDNIGHT_SKY_BRIGHTNESS);
}
