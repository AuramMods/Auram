vec3 twilightSunDirection(vec3 lightDirection) {
    return normalize(vec3(lightDirection.x, max(lightDirection.y, 0.075), lightDirection.z));
}

vec3 twilightSkyRadiance(vec3 direction, vec3 sunDirection) {
    const vec3 zenith = vec3(TWILIGHT_ZENITH_R, TWILIGHT_ZENITH_G, TWILIGHT_ZENITH_B);
    const vec3 horizon = vec3(TWILIGHT_HORIZON_R, TWILIGHT_HORIZON_G, TWILIGHT_HORIZON_B);
    float horizonGlow = exp(-4.0 * abs(direction.y));
    float sunGlow = exp(38.0 * (dot(direction, sunDirection) - 1.0));
    float belowHorizon = mix(0.32, 1.0, smoothstep(-0.48, 0.06, direction.y));
    return (mix(zenith, horizon, horizonGlow) + vec3(0.17, 0.091, 0.041) * (sunGlow * TWILIGHT_SUN_GLOW)) * (belowHorizon * TWILIGHT_SKY_BRIGHTNESS);
}
