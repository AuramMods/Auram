#include "/lib/settings.glsl"

uniform sampler2D colortex3;
uniform sampler2D colortex6;

uniform vec2 texelSize;
uniform float viewWidth;
uniform float viewHeight;

float w0(float a)
{
    return (1.0/6.0)*(a*(a*(-a + 3.0) - 3.0) + 1.0);
}

float w1(float a)
{
    return (1.0/6.0)*(a*a*(3.0*a - 6.0) + 4.0);
}

float w2(float a)
{
    return (1.0/6.0)*(a*(a*(-3.0*a + 3.0) + 3.0) + 1.0);
}

float w3(float a)
{
    return (1.0/6.0)*(a*a*a);
}

float g0(float a)
{
    return w0(a) + w1(a);
}

float g1(float a)
{
    return w2(a) + w3(a);
}

float h0(float a)
{
    return -1.0 + w1(a) / (w0(a) + w1(a));
}

float h1(float a)
{
    return 1.0 + w3(a) / (w2(a) + w3(a));
}

vec3 sampleBloomRegion(sampler2D tex, vec2 uv, vec2 origin, vec2 end)
{
    // Only texel centers strictly inside the tile were written this frame.
    vec2 first = floor(origin / texelSize + 0.5) + 0.5;
    vec2 last = ceil(end / texelSize - 0.5) - 0.5;
    if (any(lessThan(last, first))) return vec3(0.0);
    uv /= texelSize;
	vec2 iuv = floor( uv );
	vec2 fuv = fract( uv );

    float g0x = g0(fuv.x);
    float g1x = g1(fuv.x);
    float h0x = h0(fuv.x);
    float h1x = h1(fuv.x);
    float h0y = h0(fuv.y);
    float h1y = h1(fuv.y);

    vec2 low = clamp(iuv + vec2(h0x, h0y) - 0.5, first, last) * texelSize;
    vec2 high = clamp(iuv + vec2(h1x, h1y) - 0.5, first, last) * texelSize;
    return g0(fuv.y) * (g0x * texture2D(tex, low).rgb +
                        g1x * texture2D(tex, vec2(high.x, low.y)).rgb) +
           g1(fuv.y) * (g0x * texture2D(tex, vec2(low.x, high.y)).rgb +
                        g1x * texture2D(tex, high).rgb);
}

vec3 sampleBloomTile(sampler2D tex, vec2 uv, vec2 atlasScale, float tileSize, float gap)
{
    vec2 origin = vec2((0.5 - 2.0 * tileSize) * atlasScale.x + gap * texelSize.x, 0.0);
    vec2 end = min(origin + tileSize * atlasScale, vec2(0.5, 0.25) * atlasScale);
    vec2 sampleUV = uv + origin + vec2(gap > 0.0 ? 0.5 * texelSize.x : 0.0, 0.0);
    return sampleBloomRegion(tex, sampleUV, origin, end);
}

//////////////////////////////VOID MAIN//////////////////////////////
//////////////////////////////VOID MAIN//////////////////////////////
//////////////////////////////VOID MAIN//////////////////////////////
//////////////////////////////VOID MAIN//////////////////////////////
//////////////////////////////VOID MAIN//////////////////////////////

void main() {
/* DRAWBUFFERS:3 */
vec2 resScale = vec2(1920.,1080.)/max(vec2(viewWidth,viewHeight),vec2(1920.0,1080.));
vec2 texcoord = ((gl_FragCoord.xy)*2.0 + 0.5)*texelSize;

#ifdef OLD_BLOOM
    const float weights[7] = float[](1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
    const float scale = 2.0;
#else
    const float weights[7] = float[](1.0, 1.0/2.0, 1.0/3.0, 1.0/5.5, 1.0/8.0, 1.0/10.0, 1.0/12.0);
    const float scale = 3.0;
#endif

    // The first downsample is a separate half-resolution image.
    vec3 bloom = sampleBloomRegion(colortex3, texcoord / 2.0, vec2(0.0), 0.5 * resScale) * weights[0];
    bloom += sampleBloomTile(colortex6, texcoord / 4.0, resScale, 0.25, 0.0) * weights[1];
    bloom += sampleBloomTile(colortex6, texcoord / 8.0, resScale, 0.125, 2.0) * weights[2];
    bloom += sampleBloomTile(colortex6, texcoord / 16.0, resScale, 0.0625, 4.0) * weights[3];
    bloom += sampleBloomTile(colortex6, texcoord / 32.0, resScale, 0.03125, 6.0) * weights[4];
    bloom += sampleBloomTile(colortex6, texcoord / 64.0, resScale, 0.015625, 8.0) * weights[5];
    bloom += sampleBloomTile(colortex6, texcoord / 128.0, resScale, 0.0078125, 10.0) * weights[6];

    gl_FragData[0].rgb = clamp(bloom * scale, 0.0, 65000.0);
}
