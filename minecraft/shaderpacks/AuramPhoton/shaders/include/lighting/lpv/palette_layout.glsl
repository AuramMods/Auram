#if !defined INCLUDE_LIGHTING_LPV_PALETTE_LAYOUT
#define INCLUDE_LIGHTING_LPV_PALETTE_LAYOUT

// Crystal emitters follow the tint block IDs and use spare columns after the
// 16 tint colors in palette row 1. Keep the Metal transport lookup in sync.
const uint lpv_crystal_first_id = 97u;
const int lpv_crystal_count = 7;
const int lpv_crystal_palette_column = 16;

bool is_lpv_crystal_emitter(uint block_id) {
    return lpv_crystal_first_id <= block_id
        && block_id < lpv_crystal_first_id + uint(lpv_crystal_count);
}

bool is_lpv_emitter(uint block_id) {
    return (32u <= block_id && block_id < 80u) || is_lpv_crystal_emitter(block_id);
}

#endif // INCLUDE_LIGHTING_LPV_PALETTE_LAYOUT
