#include "/lib/settings.glsl"
#include "/lib/Shadow_Params.glsl"

vec4 voxyShadowPosition(vec4 position) {
    return blissShadowClipPosition(position);
}
