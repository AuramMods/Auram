#include "/lib/settings.glsl"

#define BLISS_RASTER_PALETTE
#include "/lib/lpv_palette.glsl"

layout(location = 0) out uvec2 material;

void main() {
    material = blissLpvMaterial(int(gl_FragCoord.x));
}
