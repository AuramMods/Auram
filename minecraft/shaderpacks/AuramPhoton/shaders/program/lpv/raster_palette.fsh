#include "/include/global.glsl"
#include "/include/lighting/lpv/light_colors.glsl"

layout(location = 0) out vec4 color;

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    color = vec4(pixel.y == 0 ? lpv_light_colors[pixel.x] : tint_color[min(pixel.x, 15)], 0.0);
}
