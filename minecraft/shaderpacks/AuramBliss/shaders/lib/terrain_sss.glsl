float auramTerrainFoliageMask(int blockId) {
    if (blockId == BLOCK_AIR_WAVING) return 0.55;
    if (blockId == BLOCK_GROUND_WAVING_VERTICAL || blockId == BLOCK_GRASS_SHORT ||
        blockId == BLOCK_GRASS_TALL_LOWER || blockId == BLOCK_GRASS_TALL_UPPER) return 0.60;
    return 1.0;
}

float auramTerrainScattering(int blockId) {
    if (blockId == BLOCK_SSS_WEAK || blockId == BLOCK_SSS_WEAK_2 ||
        blockId == BLOCK_GLOW_LICHEN || blockId == BLOCK_SNOW_LAYERS || blockId == BLOCK_CARPET ||
        blockId == BLOCK_AMETHYST_BUD_MEDIUM || blockId == BLOCK_AMETHYST_BUD_LARGE ||
        blockId == BLOCK_AMETHYST_CLUSTER || blockId == BLOCK_BAMBOO ||
        blockId == BLOCK_SAPLING || blockId == BLOCK_VINE) {
        return 0.75;
    }
    if (blockId == BLOCK_GROUND_WAVING || blockId == BLOCK_GROUND_WAVING_VERTICAL ||
        blockId == BLOCK_AIR_WAVING || blockId == BLOCK_GRASS_SHORT ||
        blockId == BLOCK_GRASS_TALL_UPPER || blockId == BLOCK_GRASS_TALL_LOWER ||
        blockId == BLOCK_SSS_STRONG) {
        return 1.0;
    }
    #ifdef MISC_BLOCK_SSS
        if (blockId == BLOCK_SSS_WEIRD || blockId == BLOCK_GRASS) return 0.5;
    #endif
    return 0.0;
}
