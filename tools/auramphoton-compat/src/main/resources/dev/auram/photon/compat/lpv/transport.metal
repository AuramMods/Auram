#include <metal_stdlib>
using namespace metal;

uint2 atlasPixel(uint3 p, uint side, uint columns) {
    return p.xy + uint2(p.z % columns, p.z / columns) * side;
}

uint activityIndex(uint3 group, uint side) {
    return (group.z * (side / 8) + group.y) * (side / 8) + group.x;
}

constant uint3 tileSide = uint3(8, 8, 4);

struct Reprojection {
    float4 currentFloor;
    float4 previousFloor;
    float4 currentCenter;
    float4 previousCenter;
};

int3 previousPosition(constant Reprojection &params, uint3 pixel) {
    return int3(float3(pixel) - params.previousFloor.xyz + params.currentFloor.xyz
        - params.currentCenter.xyz + params.previousCenter.xyz);
}

uint lightCanReach(constant Reprojection &params, device const uint *history, uint3 group, uint side) {
    // Float reprojection is monotonic on each axis, including coordinates with rounded pixel offsets.
    uint3 origin = group * tileSide;
    int3 lower = previousPosition(params, origin);
    int3 upper = previousPosition(params, origin + tileSide - 1);
    int limit = int(side) - 1;
    if (any(upper < int3(0)) || any(lower > int3(limit))) return 0u;
    int3 low = clamp(lower - 1, int3(0), int3(limit)) / int3(tileSide);
    int3 high = clamp(upper + 1, int3(0), int3(limit)) / int3(tileSide);
    for (int z = low.z; z <= high.z; ++z)
        for (int y = low.y; y <= high.y; ++y)
            for (int x = low.x; x <= high.x; ++x)
                if (history[activityIndex(uint3(x, y, z), side)] != 0u) return 1u;
    return 0u;
}

kernel void scheduleTiles(constant uint &side [[buffer(0)]],
                          device const uint *history [[buffer(2)]],
                          constant Reprojection &params [[buffer(4)]],
                          device uint *scheduled [[buffer(5)]],
                          uint3 group [[thread_position_in_grid]]) {
    scheduled[activityIndex(group, side)] = lightCanReach(params, history, group, side);
}

kernel void propagateLight(texture2d<float, access::read> previous [[texture(0)]],
                           texture2d<uint, access::read> blocks [[texture(1)]],
                           texture2d<float, access::read> palette [[texture(2)]],
                           texture2d<float, access::write> next [[texture(4)]],
                           constant uint &side [[buffer(0)]],
                           constant uint &columns [[buffer(1)]],
                           device uint *destination [[buffer(3)]],
                           constant Reprojection &reprojection [[buffer(4)]],
                           device const uint *scheduled [[buffer(5)]],
                           uint3 voxel [[thread_position_in_grid]],
                           uint3 group [[threadgroup_position_in_grid]],
                           uint lane [[thread_index_in_threadgroup]],
                           uint simdLane [[thread_index_in_simdgroup]]) {
    uint tile = activityIndex(group, side);
    threadgroup uint incoming;
    threadgroup uint written;
    threadgroup atomic_uint hasLight;
    if (lane == 0) {
        incoming = scheduled[tile];
        written = destination[tile];
        atomic_store_explicit(&hasLight, 0u, memory_order_relaxed);
    }
    threadgroup_barrier(mem_flags::mem_threadgroup);

    uint2 pixel = atlasPixel(voxel, side, columns);
    uint material = blocks.read(pixel).x;
    bool transparent = material == 0 || material >= 128;
    material &= 127;
    bool emits = material >= 32 && material < 80;
    float3 emission = emits ? palette.read(uint2(material - 32, 0)).rgb : float3(0);
    float3 rgb = emission * emission;
    if (incoming != 0) {
        int3 pos = previousPosition(reprojection, voxel);
        if (all(pos >= int3(0)) && all(pos < int3(side))) {
            constexpr int3 offsets[6] = {int3(1, 0, 0), int3(0, 1, 0), int3(0, 0, 1),
                                         int3(-1, 0, 0), int3(0, -1, 0), int3(0, 0, -1)};
            float3 sum = previous.read(atlasPixel(uint3(pos), side, columns)).rgb * 1.05f;
            for (int i = 0; i < 6; ++i) {
                int3 coordinate = clamp(pos + offsets[i], int3(0), int3(side - 1));
                sum += previous.read(atlasPixel(uint3(coordinate), side, columns)).rgb;
            }
            float3 average = sum * (1.0f / (7.0f * 1.05f));
            float3 tint = material >= 80 && material < 96 ? palette.read(uint2(material - 80, 1)).rgb : float3(transparent);
            rgb += average * (tint * tint);
        }
    }
    if (written != 0 || incoming != 0 || emits) next.write(float4(rgb, 0), pixel);
    bool hasSimdLight = simd_any(any(rgb != float3(0)));
    if (simdLane == 0 && hasSimdLight) atomic_fetch_or_explicit(&hasLight, 1u, memory_order_relaxed);
    threadgroup_barrier(mem_flags::mem_threadgroup);
    if (lane == 0) destination[tile] = atomic_load_explicit(&hasLight, memory_order_relaxed);
}
