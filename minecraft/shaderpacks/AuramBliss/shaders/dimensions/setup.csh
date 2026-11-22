layout (local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

const ivec3 workGroups = ivec3(6, 6, 1);

#ifdef IS_LPV_ENABLED
    #include "/lib/lpv_blocks.glsl"
    #include "/lib/lpv_palette.glsl"
#endif

void main() {
    #ifdef IS_LPV_ENABLED
        int blockId = int(gl_GlobalInvocationID.x + gl_GlobalInvocationID.y * 48);
        if (blockId >= 2048) return;
        uvec2 material = blissLpvMaterial(blockId);
        imageStore(imgBlockData, blockId, uvec4(material, 0u, 0u));
    #endif
}
