#ifdef BLISS_RASTER_LPV
    uniform sampler3D auram_bliss_light;
#else
    uniform usampler1D texBlockData;
    uniform sampler3D texLpv1;
    uniform sampler3D texLpv2;
#endif
