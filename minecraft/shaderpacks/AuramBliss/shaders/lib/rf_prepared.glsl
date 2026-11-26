#ifndef AURAM_RF_PREPARED_GLSL
#define AURAM_RF_PREPARED_GLSL

const uint rfPreparedCapacity = 8192u;
const uint rfPreparedOverflow = 0xffffffffu;

#ifdef AURAM_RF_PREPARE_KERNEL
    layout(std430, binding = 15) coherent buffer RfPreparedCache {
#else
    layout(std430, binding = 15) readonly buffer RfPreparedCache {
#endif
    uint rfPreparedCursor;
    uint rfPreparedCells[262144];
    RfPulseParameters rfPreparedPulses[8192];
};

int rfPreparedCellIndex(ivec3 cell) {
    return cell.x + 64 * (cell.y + 64 * cell.z);
}

#endif
