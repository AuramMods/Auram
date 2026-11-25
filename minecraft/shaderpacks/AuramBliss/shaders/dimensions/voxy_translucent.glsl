// Uses the Voxy fragment contract from Bliss Unstable, based on sixthsurge's Photon patch.
#define ANTIALIASING_RELATED_SETTINGS
#define WATER_RELATED_SETTINGS
#include "/lib/settings.glsl"
#include "/lib/res_params.glsl"
#include "/lib/waterBump.glsl"

layout(location = 0) out vec4 FORWARD_RENDERED_COLOR;
layout(location = 1) out vec4 TINT_AND_MASK;

vec3 voxyViewPosition() {
    vec2 uv = gl_FragCoord.xy * texelSize / RENDER_SCALE;
    vec4 position = vxProjInv * vec4(vec3(uv, gl_FragCoord.z) * 2.0 - 1.0, 1.0);
    return position.xyz / position.w;
}

vec3 voxyLinearColor(vec3 srgb) {
    return srgb * (srgb * (srgb * 0.305306011 + 0.682171111) + 0.012522878);
}

vec3 voxySkyReflection(vec3 direction) {
    const float PI = 3.141592653589793;
    vec2 uv = vec2(atan(-direction.x, -direction.z) * (0.5 / PI) + 0.5,
                   asin(clamp(direction.y, -1.0, 1.0)) / PI + 0.5);
    return texture(colortex4, (clamp(uv, 0.0, 1.0) * 256.0 + vec2(275.5, 1.5)) * texelSize).rgb / 30.0;
}

float voxySunReflection(vec3 normal, vec3 eye, vec3 light) {
    vec3 halfVector = normalize(light - eye);
    float normalHalf = clamp(dot(normal, halfVector), 0.0, 1.0);
    float normalLight = clamp(dot(normal, light), 0.0, 1.0);
    float roughness = 0.035;
    float roughnessSquared = roughness * roughness;
    float denominator = normalHalf * normalHalf * (roughnessSquared - 1.0) + 1.0;
    return normalLight * roughnessSquared * 0.02 / max(3.14159265 * denominator * denominator, 0.0001);
}

void voxy_emitFragment(VoxyFragmentParameters parameters) {
    bool water = parameters.customId == 8;
    vec3 worldNormal = vec3(uint((parameters.face >> 1) == 2),
                            uint((parameters.face >> 1) == 0),
                            uint((parameters.face >> 1) == 1))
                    * (float(int(parameters.face) & 1) * 2.0 - 1.0);
    vec3 viewPosition = voxyViewPosition();
    vec3 playerPosition = (vxModelViewInv * vec4(viewPosition, 1.0)).xyz;

    if (water && abs(worldNormal.y) > 0.1) {
        vec3 wave = normalize(getWaveNormal(playerPosition + cameraPosition, true));
        worldNormal.xz = clamp(wave.xy * WATER_WAVE_STRENGTH, -0.1, 0.1);
    }
    worldNormal = normalize(worldNormal);

    vec4 color = parameters.sampledColour * parameters.tinting;
    #ifdef WhiteWorld
        color = vec4(vec3(0.5), 1.0);
    #elif !defined Vanilla_like_water
        if (water) color = vec4(0.0, 0.0, 0.0, 1.0 / 255.0);
    #endif

    vec3 albedo = voxyLinearColor(color.rgb);
    TINT_AND_MASK = vec4(albedo, water ? 1.0 : 0.7);
    float alpha = color.a;

    vec3 direct = vec3(0.0);
    vec3 ambient = vec3(0.5);
    #ifdef OVERWORLD_SHADER
        vec3 sun = normalize(mat3(vxModelViewInv) * sunPosition) * (sunElevation > 0.0 ? 1.0 : -1.0);
        float normalLight = clamp((-15.0 + max(dot(worldNormal, sun), 0.0) * 255.0) / 240.0, 0.0, 1.0);
        direct = texelFetch(colortex4, ivec2(6, 37), 0).rgb / 80.0 * normalLight;
        vec3 normalWeights = worldNormal / dot(abs(worldNormal), vec3(1.0));
        float skylight = max(pow(worldNormal.y * 0.5 + 0.5, 0.1) + normalWeights.y * 1.5, 0.2);
        ambient = texelFetch(colortex4, ivec2(0, 37), 0).rgb / 30.0 * skylight;
    #endif

    vec3 shaded = (ambient + direct) * albedo;

    #ifdef WATER_REFLECTIONS
        vec3 eye = normalize(viewPosition);
        vec3 normal = mat3(vxModelView) * worldNormal;
        float fresnel = mix(0.02, 1.0, pow(clamp(1.0 + dot(normal, eye), 0.0, 1.0), 5.0));
        vec3 reflection = shaded;
        #if defined OVERWORLD_SHADER && defined WATER_BACKGROUND_SPECULAR
            vec3 reflected = mat3(vxModelViewInv) * reflect(eye, normal);
            reflection = voxySkyReflection(reflected);
        #endif
        reflection *= fresnel;
        #if defined OVERWORLD_SHADER && defined WATER_SUN_SPECULAR
            reflection += direct * voxySunReflection(worldNormal, normalize(playerPosition), sun);
        #endif
        float outputAlpha = mix(alpha, 1.0, fresnel);
        shaded = (shaded * alpha * (1.0 - fresnel) + reflection) / max(outputAlpha, 1.0 / 255.0);
        alpha = outputAlpha;
    #endif

    FORWARD_RENDERED_COLOR = vec4(clamp(shaded * 0.1, 0.0, 65000.0), alpha);
    #if DEBUG_VIEW == debug_NORMALS
        FORWARD_RENDERED_COLOR = vec4(worldNormal * 0.1, 1.0);
    #endif
}
