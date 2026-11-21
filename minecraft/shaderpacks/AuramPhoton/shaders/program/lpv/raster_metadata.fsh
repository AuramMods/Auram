#include "/include/global.glsl"

layout(location = 0) out vec4 light;

uniform mat4 gbufferModelViewInverse;
uniform mat4 gbufferPreviousModelView;
uniform vec3 cameraPosition;
uniform vec3 previousCameraPosition;

#include "/include/lighting/lpv/voxelization.glsl"

void main() {
    int index = int(gl_FragCoord.x);
    if (index == 0) {
        light = vec4(floor(cameraPosition), 0.0);
    } else if (index == 1) {
        light = vec4(floor(previousCameraPosition), 0.0);
    } else if (index == 2) {
        light = vec4(get_voxel_volume_center(gbufferModelViewInverse[2].xyz), 0.0);
    } else {
        vec3 priorLookDirection = vec3(gbufferPreviousModelView[0].z,
            gbufferPreviousModelView[1].z, gbufferPreviousModelView[2].z);
        light = vec4(get_voxel_volume_center(priorLookDirection), 0.0);
    }
}
