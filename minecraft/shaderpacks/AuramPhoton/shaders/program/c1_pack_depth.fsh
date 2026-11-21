#include "/include/global.glsl"

layout(location = 0) out vec4 packed_depth;

/* RENDERTARGETS: 15 */

uniform sampler2D depthtex0;
uniform sampler2D depthtex1;
uniform sampler2D dhDepthTex;
uniform sampler2D dhDepthTex1;

// The deferred passes have finished reading the combined depth. Store the
// four original depths in full precision for the translucent blend pass.
void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);
    packed_depth = vec4(
        texelFetch(dhDepthTex, texel, 0).x,
        texelFetch(depthtex1, texel, 0).x,
        texelFetch(depthtex0, texel, 0).x,
        texelFetch(dhDepthTex1, texel, 0).x
    );
}
