/////// ALL OF THIS IS BASED OFF OF THE DISTANT HORIZONS EXAMPLE PACK BY NULL

uniform mat4 dhPreviousProjection;
uniform mat4 dhProjectionInverse;
uniform mat4 dhProjection;

vec3 toScreenSpace_DH( vec2 texcoord, float depth, float DHdepth ) {

	vec4 viewPos = vec4(0.0);
	vec3 feetPlayerPos = vec3(0.0);
	vec4 iProjDiag = vec4(0.0);

	#ifdef DISTANT_HORIZONS
    	if (depth < 1.0) {
	#endif
			iProjDiag = vec4(gbufferProjectionInverse[0].x, gbufferProjectionInverse[1].y, gbufferProjectionInverse[2].zw);

    		feetPlayerPos = vec3(texcoord, depth) * 2.0 - 1.0;
    		viewPos = iProjDiag * feetPlayerPos.xyzz + gbufferProjectionInverse[3];
			viewPos.xyz /= viewPos.w;
	
	#ifdef DISTANT_HORIZONS
		} else {
			iProjDiag = vec4(dhProjectionInverse[0].x, dhProjectionInverse[1].y, dhProjectionInverse[2].zw);

    		feetPlayerPos = vec3(texcoord, DHdepth) * 2.0 - 1.0;
    		viewPos = iProjDiag * feetPlayerPos.xyzz + dhProjectionInverse[3];
			viewPos.xyz /= viewPos.w;
		}
	#endif

    return viewPos.xyz;
}

#ifdef AURAM_VOXY_DEPTH
bool auramHasVoxyDepth(float depth) {
    return depth > 0.0 && depth < 1.0;
}

float auramAxialDepth(float depth, mat4 projectionInverse) {
    vec2 view = projectionInverse[2].zw * (depth * 2.0 - 1.0) + projectionInverse[3].zw;
    return -view.x / view.y;
}

bool auramVoxyFrontIsLod(float nearDepth, float lodDepth) {
    return auramHasVoxyDepth(lodDepth) &&
        (nearDepth >= 1.0 || auramAxialDepth(lodDepth, dhProjectionInverse) < auramAxialDepth(nearDepth, gbufferProjectionInverse));
}

vec3 auramVoxyViewPosition(vec2 texcoord, float lodDepth) {
    vec3 clipPosition = vec3(texcoord, lodDepth) * 2.0 - 1.0;
    vec4 viewPosition = dhProjectionInverse * vec4(clipPosition, 1.0);
    return viewPosition.xyz / viewPosition.w;
}
#endif

vec3 auramSurfaceViewPosition(vec2 texcoord, float depth, float lodDepth) {
    #ifdef AURAM_VOXY_DEPTH
        if (depth >= 1.0 && lodDepth > 0.0 && lodDepth < 1.0) {
            return auramVoxyViewPosition(texcoord, lodDepth);
        }
    #endif
    return toScreenSpace_DH(texcoord, depth, lodDepth);
}

vec3 auramFrontViewPosition(vec2 texcoord, float depth, float lodDepth) {
    vec3 viewPosition = auramSurfaceViewPosition(texcoord, depth, lodDepth);
    #ifdef AURAM_VOXY_DEPTH
        // A Voxy translucent pixel can be in front of the near opaque depth.
        if (depth < 1.0 && lodDepth > 0.0 && lodDepth < 1.0) {
            vec3 lodPosition = auramVoxyViewPosition(texcoord, lodDepth);
            if (lodPosition.z > viewPosition.z) return lodPosition;
        }
    #endif
    return viewPosition;
}

// Use the same view direction for terrain, LODs and empty sky. Only a real depth can stop the ray.
vec4 volumeViewRay(vec2 texcoord, float depth, float dhDepth) {
    vec3 direction = normalize(toScreenSpace_DH(texcoord, 0.5, 1.0));
    float surfaceDistance = -1.0;
    #ifdef AURAM_VOXY_DEPTH
    if (depth < 1.0 || (dhDepth > 0.0 && dhDepth < 1.0)) {
        surfaceDistance = length(auramFrontViewPosition(texcoord, depth, dhDepth));
    }
    #else
    if (depth < 1.0) {
        surfaceDistance = length(toScreenSpace_DH(texcoord, depth, 1.0));
    }
    #ifdef DISTANT_HORIZONS
        else if (dhDepth > 0.0 && dhDepth < 1.0) {
            surfaceDistance = length(auramSurfaceViewPosition(texcoord, depth, dhDepth));
        }
    #endif
    #endif
    return vec4(direction, surfaceDistance);
}

vec3 toClipSpace3_DH( vec3 viewSpacePosition, bool depthCheck ) {

	#if defined DISTANT_HORIZONS || defined AURAM_VOXY_DEPTH
		mat4 projectionMatrix = depthCheck ? dhProjection : gbufferProjection;
   		return projMAD(projectionMatrix, viewSpacePosition) / -viewSpacePosition.z * 0.5 + 0.5;
	#else
    	return projMAD(gbufferProjection, viewSpacePosition) / -viewSpacePosition.z * 0.5 + 0.5;
	#endif

}

mat4 DH_shadowProjectionTweak( in mat4 projection){
	
	#ifdef DH_SHADOWPROJECTIONTWEAK
		
		float _far = (3.0 * far);

		#ifdef DISTANT_HORIZONS
		    _far = 2.0 * dhFarPlane;
		#endif
		
		mat4 newProjection = projection;
		newProjection[2][2] = -2.0 / _far;
		newProjection[3][2] = 0.0;

		return newProjection;
	#else
		return projection;
	#endif
}
