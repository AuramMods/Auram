float netherSmokeField(vec3 pos, float scale, float animation) {
    vec3 samplePos = pos * vec3(1.0, 1.0 / 48.0, 1.0);
    samplePos.xz /= scale;
    float bend = pow(max(pos.y - 30.0, 0.0) / 15.0, 2.1);

    float plumes = texture2D(noisetex, (samplePos.xz + bend) / 256.0).b;
    float floorPlumes = clamp(0.3 - exp(plumes * -6.0), 0.0, 1.0);
    plumes *= plumes;

    float erosion = densityAtPosFog(samplePos * 400.0 - animation - bend * 10.0) * 0.7 + 0.3;
    float roofToFloorFalloff = exp(max(100.0 - pos.y, 0.0) / -15.0);
    float floorFalloff = pow(exp(max(pos.y - 31.0, 0.0) / -3.0), 2.0);
    return max((roofToFloorFalloff - plumes * (1.0 - erosion)) * 2.0,
               clamp((floorFalloff - floorPlumes * 0.5) * erosion, 0.0, 1.0));
}

float netherSmokePlumeDensity(float field, float height) {
    return min(field * pow(clamp((100.0 - height) / 30.0, 0.0, 1.0), 4.0), 1.0);
}

float netherSmokePlumeLight(float field) {
    return exp(-15.0 * field);
}

float netherSmokeUpperDensity(float height) {
    return 0.001 * pow(clamp((height - 40.0) / 50.0, 0.0, 1.0), 3.0);
}
