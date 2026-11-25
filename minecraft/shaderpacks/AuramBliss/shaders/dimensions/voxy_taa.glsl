{
    #include "/lib/TAA_jitter.glsl"
    return offsets[framemod8] * texelSize * float(auramVoxyTaaEnabled);
}
