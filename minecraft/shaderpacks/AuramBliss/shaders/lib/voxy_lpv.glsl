#ifdef VOXY_LPV_OCCUPANCY
uniform usampler3D voxyLpvOccupancy;
uniform ivec4 voxyLpvBounds;

uint voxyLpvMaterial(uint nearMaterial, ivec3 voxel) {
    if (nearMaterial != 0u) return nearMaterial;
    ivec3 cached = voxel + voxyLpvBounds.xyz;
    if (any(lessThan(cached, ivec3(0))) || any(greaterThanEqual(cached, ivec3(voxyLpvBounds.w)))) return 0u;
    return texelFetch(voxyLpvOccupancy, cached, 0).r;
}
#endif
