#include "/lib/settings.glsl"
#include "/lib/hsv.glsl"
#include "/lib/util.glsl"
#include "/lib/lpv_unpack.glsl"

uniform vec3 cameraPosition;
uniform vec3 previousCameraPosition;
uniform sampler3D previous_light;
uniform usampler2D voxel_sampler;
uniform usampler2D light_data_sampler;
uniform int voxel_atlas_columns;

#include "/lib/lpv_common.glsl"

#if defined RF_FLOW_STREAKS && defined AURAM_RF_BACKEND && defined AURAM_BLISS_RF_LPV
    uniform float frameTimeCounter;
    #include "/lib/rf_field.glsl"
#endif

#if defined AURAM_LIGHT_BACKEND || defined AURAM_TINKERS_FLUID_BACKEND
    #include "/lib/blocks.glsl"
    #include "/lib/tinkers_fluid_light.glsl"
#endif

#ifdef AURAM_BLISS_COMPUTE_TRANSPORT
    layout(local_size_x = 8, local_size_y = 8, local_size_z = 4) in;
    layout(rgba8, binding = 0) uniform writeonly image3D next_light;
    shared vec4 previousTile[600];
    shared uint occupancyTile[600];
#else
    flat in int auram_photon_layer;
    layout(location = 0) out vec4 light;
#endif

uint voxelId(ivec3 voxel) {
    if (any(lessThan(voxel, ivec3(0))) || any(greaterThanEqual(voxel, ivec3(LpvSize)))) return 0u;
    ivec2 tile = ivec2(voxel.z % voxel_atlas_columns, voxel.z / voxel_atlas_columns);
    return texelFetch(voxel_sampler, voxel.xy + tile * int(LpvSize), 0).r;
}

uvec2 materialData(uint id) {
    return texelFetch(light_data_sampler, ivec2(int(id), 0), 0).rg;
}

vec4 previousEncodedLight(ivec3 voxel) {
    if (any(lessThan(voxel, ivec3(0))) || any(greaterThanEqual(voxel, ivec3(LpvSize)))) return vec4(0.0);
    return texelFetch(previous_light, voxel, 0);
}

vec4 decodeLight(vec4 encoded) {
    vec4 hsvSky = vec4(RgbToHsv(encoded.rgb), encoded.a);
    hsvSky.zw = exp2(hsvSky.zw * LpvBlockSkyRange) - 1.0;
    return vec4(HsvToRgb(hsvSky.xyz), hsvSky.w);
}

#ifdef AURAM_BLISS_COMPUTE_TRANSPORT
    const ivec3 tileStride = ivec3(1, 10, 100);

    int tileIndex(ivec3 position) {
        return position.x + 10 * position.y + 100 * position.z;
    }

    void loadTile(ivec3 movement) {
        ivec3 origin = ivec3(gl_WorkGroupID * gl_WorkGroupSize) - 1;
        for (uint index = gl_LocalInvocationIndex; index < 600u; index += 256u) {
            ivec3 local = (ivec3(index) / tileStride) % ivec3(10, 10, 6);
            bvec3 face = equal(local, clamp(local, ivec3(1), ivec3(gl_WorkGroupSize)));
            int interiorAxes = int(face.x) + int(face.y) + int(face.z);
            // The six-neighbor stencil never addresses a tile edge or corner.
            if (interiorAxes < 2) continue;
            ivec3 position = origin + local;
            vec4 encoded = previousEncodedLight(position + movement);
            bool lit = encoded != vec4(0.0);
            previousTile[index] = lit ? decodeLight(encoded) : encoded;
            occupancyTile[index] = lit || interiorAxes == 3 ? voxelId(position) : 0u;
        }
        barrier();
    }
#endif

void main() {
    const ivec3 directions[6] = ivec3[6](ivec3(-1, 0, 0), ivec3(1, 0, 0),
        ivec3(0, -1, 0), ivec3(0, 1, 0), ivec3(0, 0, -1), ivec3(0, 0, 1));
    ivec3 movement = ivec3(floor(cameraPosition) - floor(previousCameraPosition));
    #ifdef AURAM_BLISS_COMPUTE_TRANSPORT
        ivec3 voxel = ivec3(gl_GlobalInvocationID);
        ivec3 local = ivec3(gl_LocalInvocationID) + 1;
        loadTile(movement);
        uint id = occupancyTile[tileIndex(local)];
    #else
        ivec3 voxel = ivec3(ivec2(gl_FragCoord.xy), auram_photon_layer);
        uint id = voxelId(voxel);
    #endif
    uvec2 material = id == 0u ? uvec2(0u) : materialData(id);
    uint mask = id == 0u ? 0xffffu : material.y >> 24u;
    vec3 tint = id == 0u ? vec3(1.0) : srgbToLinear(blissUnpackMaterial(material.y).rgb);

    vec4 incoming = vec4(0.0);
    vec4 emission = blissUnpackMaterial(material.x);
    vec3 fluidLight = vec3(0.0);
    #if defined AURAM_LIGHT_BACKEND || defined AURAM_TINKERS_FLUID_BACKEND
        fluidLight = tinkersFluidEmission(voxel, int(LpvSize), cameraPosition);
        if (replacesFluidLightFallback(id, fluidLight)) {
            tint = vec3(0.0);
            emission = vec4(0.0);
        }
    #endif
    if (any(greaterThan(tint, vec3(0.0)))) {
        for (int direction = 0; direction < 6; direction++) {
            if (((mask >> direction) & 1u) == 0u) continue;
            // An unlit voxel has no contribution, regardless of its face mask.
            #ifdef AURAM_BLISS_COMPUTE_TRANSPORT
                int neighborIndex = tileIndex(local + directions[direction]);
                vec4 neighborLight = previousTile[neighborIndex];
                if (neighborLight == vec4(0.0)) continue;
                uint neighborId = occupancyTile[neighborIndex];
            #else
                ivec3 neighbor = voxel + directions[direction];
                vec4 encoded = previousEncodedLight(neighbor + movement);
                if (encoded == vec4(0.0)) continue;
                uint neighborId = voxelId(neighbor);
            #endif
            uint neighborMask = neighborId == 0u ? 0xffffu : materialData(neighborId).y >> 24u;
            if (((neighborMask >> (direction ^ 1)) & 1u) == 0u) continue;
            #ifdef AURAM_BLISS_COMPUTE_TRANSPORT
                incoming += neighborLight;
            #else
                incoming += decodeLight(encoded);
            #endif
        }
        incoming *= (1.0 / 6.0) * vec4(vec3(LPV_BLOCK_FALLOFF), 0.96);
        incoming.rgb *= tint;
    }
    if (id != 0u && emission.a > 0.0) {
        vec3 hsv = RgbToHsv(srgbToLinear(emission.rgb));
        hsv.z = exp2(emission.a * 255.0) - 1.0;
        incoming.rgb += HsvToRgb(hsv);
    }

    #if defined RF_FLOW_STREAKS && defined AURAM_RF_BACKEND && defined AURAM_BLISS_RF_LPV
        incoming.rgb += rfVoxelEmission(voxel, int(LpvSize), cameraPosition);
    #endif

    incoming.rgb += fluidLight;
    vec4 encoded = incoming;
    if (incoming != vec4(0.0)) {
        vec4 hsvSky = vec4(RgbToHsv(incoming.rgb), incoming.a);
        hsvSky.zw = log2(hsvSky.zw + 1.0) / LpvBlockSkyRange;
        encoded = vec4(HsvToRgb(hsvSky.xyz), hsvSky.w);
    }
    #ifdef AURAM_BLISS_COMPUTE_TRANSPORT
        // The extra last layer contains the handheld palette.
        imageStore(next_light, voxel, encoded);
    #else
        light = encoded;
    #endif
}
