#include "/lib/taa_option.glsl"

{
#ifdef TAA
    #include "/lib/TAA_jitter.glsl"
    return offsets[framemod8] * texelSize;
#else
    return vec2(0.0);
#endif
}
