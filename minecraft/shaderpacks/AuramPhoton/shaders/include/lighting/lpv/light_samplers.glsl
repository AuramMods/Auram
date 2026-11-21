#ifdef AURAM_PHOTON_RASTER_LPV
#ifdef AURAM_PHOTON_METAL_LPV
uniform sampler2DRect auram_photon_metal_light;
#else
uniform sampler3D auram_photon_light;
#endif
#else
uniform sampler3D light_sampler_a;
uniform sampler3D light_sampler_b;
#endif
