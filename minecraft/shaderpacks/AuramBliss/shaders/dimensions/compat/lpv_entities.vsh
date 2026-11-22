#include "/lib/settings.glsl"
#include "/lib/blocks.glsl"
#include "/lib/entities.glsl"

layout(location = BLISS_POSITION_ATTRIBUTE) in vec3 iris_Position;
layout(location = BLISS_ENTITY_ATTRIBUTE) in ivec3 iris_Entity;

uniform mat4 iris_ModelViewMat;
uniform vec3 iris_ChunkOffset;
uniform mat4 shadowModelViewInverse;
uniform vec3 cameraPosition;
uniform bool blissRawPosition;
uniform int blissRenderStage;
uniform usampler2D auram_bliss_palette;

// Keep the real tag when Oculus supplies one. Both held block items and real block entities set iris_Entity.y.
#define renderStage ((blissRenderStage == MC_RENDER_STAGE_ENTITIES || blissRenderStage == MC_RENDER_STAGE_BLOCK_ENTITIES) \
    ? blissRenderStage : ((iris_Entity.z > 0 && iris_Entity.z < 1200) || !(iris_Entity.y > 0 && iris_Entity.y < 500) \
    ? MC_RENDER_STAGE_ENTITIES : MC_RENDER_STAGE_BLOCK_ENTITIES))
#define entityId iris_Entity.x
#define blockEntityId iris_Entity.y
#define currentRenderedItemId iris_Entity.z
#define mc_Entity vec4(0.0)
#define at_midBlock vec4(0.0)

#include "/lib/voxel_common.glsl"
#include "/lib/voxel_write.glsl"

void main() {
    vec3 position = iris_Position + iris_ChunkOffset;
    if (!blissRawPosition) {
        position = (shadowModelViewInverse * iris_ModelViewMat * vec4(position, 1.0)).xyz;
    }
    PopulateShadowVoxel(position);
}
