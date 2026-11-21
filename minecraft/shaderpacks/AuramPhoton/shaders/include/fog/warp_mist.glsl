#if defined DISTANT_HORIZONS && defined AURAM_PHOTON_DH_WARP_MIST
uniform float auramWarpMist;
uniform vec3 fogColor;

vec3 warp_mist_color() {
    return srgb_eotf_inv(fogColor) * rec709_to_working_color;
}
#endif
