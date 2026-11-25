// LPV block brightness scale
const float LpvBlockBrightness = LPV_BLOCK_BRIGHTNESS;


float lpvCurve(float values) {
    // return values;
    return pow(1.0 - sqrt(1.0-values), 2.0);
}

vec4 SampleLpvLinear(const in vec3 lpvPos) {
    vec3 texcoord = lpvPos / LpvSize3;

    #ifdef BLISS_RASTER_LPV
        // The last slice holds the handheld material colors.
        float z = clamp(lpvPos.z, 0.5, float(LpvSize) - 0.5) / float(LpvSize + 1u);
        vec4 lpvSample = textureLod(auram_bliss_light, vec3(texcoord.xy, z), 0);
    #else
        vec4 lpvSample = (frameCounter % 2) == 0
            ? textureLod(texLpv1, texcoord, 0)
            : textureLod(texLpv2, texcoord, 0);
    #endif

    vec3 hsv = RgbToHsv(lpvSample.rgb);
    hsv.z = lpvCurve(hsv.b) * LpvBlockSkyRange.x;
    lpvSample.rgb = HsvToRgb(hsv);

    return lpvSample;
}

vec3 GetLpvBlockLight(const in vec4 lpvSample) {
    return LpvBlockBrightness * lpvSample.rgb;
}

float GetLpvSkyLight(const in vec4 lpvSample) {
    float skyLight = saturate(lpvSample.a);
    return skyLight*skyLight;
}
