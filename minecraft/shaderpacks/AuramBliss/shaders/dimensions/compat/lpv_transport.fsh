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

flat in int auram_photon_layer;
layout(location = 0) out vec4 light;

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

void main() {
    const ivec3 directions[6] = ivec3[6](ivec3(-1, 0, 0), ivec3(1, 0, 0),
        ivec3(0, -1, 0), ivec3(0, 1, 0), ivec3(0, 0, -1), ivec3(0, 0, 1));
    ivec3 voxel = ivec3(ivec2(gl_FragCoord.xy), auram_photon_layer);
    ivec3 movement = ivec3(floor(cameraPosition) - floor(previousCameraPosition));
    uint id = voxelId(voxel);
    uvec2 material = id == 0u ? uvec2(0u) : materialData(id);
    uint mask = id == 0u ? 0xffffu : material.y >> 24u;
    vec3 tint = id == 0u ? vec3(1.0) : srgbToLinear(blissUnpackMaterial(material.y).rgb);

    vec4 incoming = vec4(0.0);
    if (any(greaterThan(tint, vec3(0.0)))) {
        for (int direction = 0; direction < 6; direction++) {
            if (((mask >> direction) & 1u) == 0u) continue;
            ivec3 neighbor = voxel + directions[direction];
            // An unlit voxel has no contribution, regardless of its face mask.
            vec4 encoded = previousEncodedLight(neighbor + movement);
            if (encoded == vec4(0.0)) continue;

            uint neighborId = voxelId(neighbor);
            uint neighborMask = neighborId == 0u ? 0xffffu : materialData(neighborId).y >> 24u;
            if (((neighborMask >> (direction ^ 1)) & 1u) == 0u) continue;
            vec4 hsvSky = vec4(RgbToHsv(encoded.rgb), encoded.a);
            hsvSky.zw = exp2(hsvSky.zw * LpvBlockSkyRange) - 1.0;
            incoming += vec4(HsvToRgb(hsvSky.xyz), hsvSky.w);
        }
        incoming *= (1.0 / 6.0) * 0.96;
        incoming.rgb *= tint;
    }

    vec4 emission = blissUnpackMaterial(material.x);
    if (id != 0u && emission.a > 0.0) {
        vec3 hsv = RgbToHsv(srgbToLinear(emission.rgb));
        hsv.z = exp2(emission.a * 255.0) - 1.0;
        incoming.rgb += HsvToRgb(hsv);
    }

    if (incoming == vec4(0.0)) {
        light = incoming;
    } else {
        vec4 hsvSky = vec4(RgbToHsv(incoming.rgb), incoming.a);
        hsvSky.zw = log2(hsvSky.zw + 1.0) / LpvBlockSkyRange;
        light = vec4(HsvToRgb(hsvSky.xyz), hsvSky.w);
    }
}
