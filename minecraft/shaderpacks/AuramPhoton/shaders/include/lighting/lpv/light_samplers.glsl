#ifdef AURAM_PHOTON_RASTER_LPV
uniform sampler3D auram_photon_light;
#else
uniform sampler3D light_sampler_a;
uniform sampler3D light_sampler_b;
#endif
