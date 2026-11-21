#version 150 core

uniform sampler2D depth;
uniform sampler2D altDepth;
uniform sampler2D auramPreHandDepth;
uniform sampler2D auramDistantDepth;
uniform mat4 auramVanillaProjection;
uniform mat4 auramDistantProjection;
uniform float auramFocusScale;
uniform int auramHasDistantDepth;
uniform int auramResetFocus;
uniform float lastFrameTime;
uniform float decay;

out float iris_fragColor;

ivec2 center_texel(sampler2D depth_texture) {
    return ivec2(vec2(textureSize(depth_texture, 0)) * (0.5 * auramFocusScale));
}

void main() {
    ivec2 center = center_texel(auramPreHandDepth);
    float current = texelFetch(auramPreHandDepth, center, 0).r;
    if (current >= 1.0 && auramHasDistantDepth != 0) {
        float front = texelFetch(depth, center, 0).r;
        if (front >= 1.0 || front <= 0.56) {
            float distant = texelFetch(auramDistantDepth, center_texel(auramDistantDepth), 0).r;
            if (distant < 1.0) {
                float ndc = 2.0 * distant - 1.0;
                vec2 z_column = auramDistantProjection[2].zw;
                vec2 constant = auramDistantProjection[3].zw;
                float view_z = (constant.x - ndc * constant.y) / (ndc * z_column.y - z_column.x);
                if (view_z < 0.0 && !isinf(view_z)) {
                    vec2 clip = auramVanillaProjection[2].zw * view_z + auramVanillaProjection[3].zw;
                    current = 0.5 * (clip.x / clip.y) + 0.5;
                }
            }
        }
    }

    float old = texture(altDepth, vec2(0.5)).r;
    float response = 1.0 - exp(-decay * lastFrameTime);
    iris_fragColor = (auramResetFocus != 0 || isnan(old)) ? current : mix(old, current, response);
}
