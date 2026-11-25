#ifndef AURAM_VOXY_OPAQUE_DEPTH
#define AURAM_VOXY_OPAQUE_DEPTH

// Project a 0.6-block surface match tolerance onto the view depth axis.
float auramVoxyOpaqueTolerance(vec3 lodPosition) {
    return 0.6 * -lodPosition.z / max(length(lodPosition), 1.0);
}

#endif
