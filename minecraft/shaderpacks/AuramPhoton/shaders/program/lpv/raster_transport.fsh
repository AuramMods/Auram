#include "/include/global.glsl"

flat in int auram_photon_layer;
layout(location = 0) out vec4 light;

uniform sampler3D previous_light;
uniform usampler3D voxel_sampler;
uniform sampler2D light_data_sampler;

uniform mat4 gbufferModelViewInverse;
uniform mat4 gbufferPreviousModelView;
uniform vec3 cameraPosition;
uniform vec3 previousCameraPosition;

#include "/include/lighting/lpv/floodfill.glsl"

void main() {
    ivec3 voxel = ivec3(ivec2(gl_FragCoord.xy), auram_photon_layer);
    light = evaluate_lpv_voxel(previous_light, voxel);
}
