#include "/lib/settings.glsl"
#include "/lib/Shadow_Params.glsl"

vec4 voxyShadowPosition(vec4 position) {
    #ifdef DISTORT_SHADOWMAP
        position = BiasShadowProjection(position);
    #endif
    position.z /= 6.0;
    return position;
}
