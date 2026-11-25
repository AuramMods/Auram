#if defined DISTANT_HORIZONS
    #define AURAM_LOD_DEPTH
    #define AURAM_LOD_UV_SCALE vec2(1.0)
#elif defined VOXY
    #define AURAM_LOD_DEPTH
    #define AURAM_VOXY_DEPTH
    #define AURAM_LOD_UV_SCALE (1.0 / RENDER_SCALE)
    // Use Voxy's separate opaque and translucent depths with the existing LoD composites.
    #define dhDepthTex vxDepthTexTrans
    #define dhDepthTex1 vxDepthTexOpaque
    #define dhProjection vxProj
    #define dhProjectionInverse vxProjInv
    #define dhPreviousProjection vxProjPrev
    #define dhNearPlane vxNear
    #define dhFarPlane vxFar
    #define dhRenderDistance vxRenderDistanceBlocks
#endif

#ifdef AURAM_LOD_DEPTH
    #define sampleLodDepth(depth, uv) texture2D(depth, (uv) * AURAM_LOD_UV_SCALE)
#endif
