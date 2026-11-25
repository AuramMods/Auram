const float smokeTop = smokeFloor + float(TWILIGHT_SMOKE_THICKNESS);
const int smokeMaxSamples = 6;
const float smokeSampleSpacing = 12.0;

float twilightSmokeNoise(vec2 pos) {
    vec2 blend = fract(pos);
    blend = blend * blend * (3.0 - 2.0 * blend);
    return texture2DLod(noisetex, (floor(pos) + blend + 0.5) / 512.0, 0.0).a;
}

float smokeDensity(vec3 pos) {
    // Advect the whole density field with a horizontal wind, including its bends and gaps.
    vec2 windAxes = mat2(0.8, -0.6, 0.6, 0.8) * pos.xz;
    windAxes.x -= frameTimeCounter * TWILIGHT_SMOKE_SPEED;
    vec2 field = windAxes / (vec2(96.0, 29.0) * TWILIGHT_SMOKE_SCALE);
    float warp = twilightSmokeNoise(field * 0.53 + vec2(29.4, 51.7));
    float bank = twilightSmokeNoise(field * vec2(0.9, 0.62) + vec2(9.1, 131.2));
    field.y += (warp - 0.5) * 1.1;

    // A broken ridge creates tapered, meandering drafts with varying width and altitude.
    float ridge = abs(twilightSmokeNoise(field + vec2(12.2, 22.6)) - 0.5);
    float width = mix(0.09, 0.24, bank);
    float draft = (1.0 - smoothstep(0.025, width, ridge)) * smoothstep(0.35, 0.67, bank);
    if (draft <= 0.0) return 0.0;

    float height = (pos.y - smokeFloor) * (64.0 / float(TWILIGHT_SMOKE_THICKNESS));
    float center = mix(10.0, 49.0, warp);
    float thickness = mix(6.0, 22.0, bank);
    float profile = 1.0 - smoothstep(0.1, 1.0, abs(height - center) / thickness);
    if (profile <= 0.0) return 0.0;
    float layerEdge = smoothstep(0.0, 6.0, height) * (1.0 - smoothstep(54.0, 64.0, height));
    float detail = densityAtPosFog(vec3(windAxes.x * 2.2, height * 3.0, windAxes.y * 2.2));
    return draft * profile * layerEdge * mix(0.56, 1.0, detail) * TWILIGHT_SMOKE_DENSITY;
}
