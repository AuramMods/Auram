#define AURAM_RF_PREPARE_KERNEL

#include "/lib/settings.glsl"

#ifdef RF_FLOW_STREAKS
uniform float frameTimeCounter;
uniform float viewHeight;

#include "/lib/rf_field.glsl"
#include "/lib/rf_pulse.glsl"
#include "/lib/rf_routes.glsl"
#include "/lib/rf_prepared.glsl"

layout(local_size_x = 128) in;

void rfPrepareCell(ivec3 cell) {
    uvec4 data = texelFetch(auramRfVolume, cell, 0);
    if (data.rgb == uvec3(0u)) return;

    ivec4 routes[12];
    int count = rfFindRoutes(data.rgb, routes);
    int cellIndex = rfPreparedCellIndex(cell);
    uint base = atomicAdd(rfPreparedCursor, uint(count));
    if (base + uint(count) > rfPreparedCapacity) {
        rfPreparedCells[cellIndex] = rfPreparedOverflow;
        return;
    }

    float phase = rfCircuitPhase(data.a, rfTravelPhase());
    uint stored = 0u;
    for (int i = 0; i < count; i++) {
        ivec4 route = routes[i];
        RfPulseParameters pulse;
        if (rfPreparePulse(cell, phase, route.x, uint(route.y), route.z, uint(route.w), pulse)) {
            rfPreparedPulses[base + stored] = pulse;
            stored++;
        }
    }
    rfPreparedCells[cellIndex] = (base << 4u) | stored;
}

void main() {
    if (gl_LocalInvocationIndex == 0u) rfPreparedCursor = 0u;
    memoryBarrierBuffer();
    barrier();
    if (!auramRfActive) return;

    ivec3 minimum = ivec3(auramRfMinimum);
    ivec3 size = ivec3(auramRfMaximum) - minimum;
    int slice = size.x * size.y;
    int total = slice * size.z;
    for (int index = int(gl_LocalInvocationIndex); index < total; index += int(gl_WorkGroupSize.x)) {
        ivec3 offset = ivec3(index % size.x, (index / size.x) % size.y, index / slice);
        rfPrepareCell(minimum + offset);
    }
}
#endif
