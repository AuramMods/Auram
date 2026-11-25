#if defined AURAM_VOXY_DEPTH && defined OVERWORLD_SHADER && SSS_TYPE != 0 && defined BASIC_SHADOW_FILTER && defined Variable_Penumbra_Shadows
    #define AURAM_VOXY_NEAR_SSS
    uniform float vxNearRenderDistance;

    // Zero in the shadow buffer means the search did not run for this pixel.
    const float voxyScatteringDepthOffset = 1.0 / 256.0;

    float auramVoxyScatteringWeight(vec2 cameraRelativeXZ) {
        float distanceBeyondNear = length(cameraRelativeXZ) - vxNearRenderDistance;
        return 1.0 - smoothstep(16.0, 32.0, distanceBeyondNear);
    }
#endif
