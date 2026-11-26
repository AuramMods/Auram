// THANK YOU SIXTHSURGE FOR ALLOWING USAGE OF PHOTON CODE TO BUILD THIS VOXY IMPLEMENTATION FROM: https://github.com/sixthsurge/photon
#define ANTIALIASING_RELATED_SETTINGS
#define SUB_SURFACE_SCATTERING_RELATED_SETTINGS
#include "/lib/settings.glsl"
#include "/lib/blocks.glsl"
#include "/lib/terrain_sss.glsl"

bool voxyUsesNearDepth = false;

#ifdef DH_OVERDRAW_PREVENTION
#include "/lib/res_params.glsl"
#include "/lib/dh_chunk_transition.glsl"
#include "/lib/voxy_opaque_depth.glsl"

bool voxy_keepOpaqueWithNearDepth(float nearDepth, float nearReadiness) {
    vec2 uv = gl_FragCoord.xy * texelSize / RENDER_SCALE;
    vec4 view = vxProjInv * vec4(vec3(uv, gl_FragCoord.z) * 2.0 - 1.0, 1.0);
    vec3 lod = view.xyz / view.w;
    float tolerance = auramVoxyOpaqueTolerance(lod);
    float gap = dhViewDepth(nearDepth, gbufferProjectionInverse) + lod.z;
    if (gap < -tolerance) return false;
    // A deeper near fragment can be a cave wall under a section still loading.
    // Let a settled near section reveal real openings in the cached surface.
    if (gap > tolerance) return nearReadiness < 1.0;

    // Both passes wrote this surface. Choose one material per pixel so encoded
    // normals and lightmaps survive the transition intact.
    vec3 relativePosition = mat3(vxModelViewInv) * lod;
    float nearDistance = vxNearRenderDistance;
    float coverage = smoothstep(max(nearDistance - 16.0, 0.0), nearDistance + 8.0,
                                dhChunkDistance(relativePosition));
    if (nearReadiness >= 0.0) coverage = mix(1.0, coverage, nearReadiness);
    voxyUsesNearDepth = dhTransitionDither(frameCounter) < coverage;
    return voxyUsesNearDepth;
}
#endif

vec4 encode (vec3 n, vec2 lightmaps){
	n.xy = n.xy / dot(abs(n), vec3(1.0));
	n.xy = n.z <= 0.0 ? (1.0 - abs(n.yx)) * sign(n.xy) : n.xy;
    vec2 encn = clamp(n.xy * 0.5 + 0.5,-1.0,1.0);

    return vec4(encn,vec2(lightmaps.x,lightmaps.y));
}

float encodeVec2(vec2 a){
    const vec2 constant1 = vec2( 1., 256.) / 65535.;
    vec2 temp = floor( a * 255. );
	return temp.x*constant1.x+temp.y*constant1.y;
}

float encodeVec2(float x,float y){
    return encodeVec2(vec2(x,y));
}

float writeVoxyDepth(in float depth){
    return depth*depth*65000.0;
}

float readVoxyDepth(sampler2D colortex, in ivec2 coords){
    return sqrt(texelFetch(colortex, coords, 0).x/65000.0);
}

vec3 viewToWorld(vec3 viewPos) {
    vec4 pos;
    pos.xyz = viewPos;
    pos.w = 0.0;
    pos = gbufferModelViewInverse * pos ;
    return pos.xyz;
}

vec3 worldToView(vec3 worldPos) {
    vec4 pos = vec4(worldPos, 0.0);
    pos = gbufferModelView * pos;
    return pos.xyz;
}

/*
struct VoxyFragmentParameters {
    vec4 sampledColour;
    vec2 tile;
    vec2 uv;
    uint face;
    uint modelId;
    vec2 lightMap;
    vec4 tinting;
    uint customId;//Same as iris's modelId
};
*/

layout(location = 0) out vec4 DEFERRED_DATA;
layout(location = 1) out vec4 SPECULAR_DATA;
layout(location = 2) out vec4 NORMAL_AND_AO;
#ifdef DH_OVERDRAW_PREVENTION
layout(location = 3) out float OPAQUE_LOD_DEPTH;
#endif

void voxy_emitFragment(VoxyFragmentParameters parameters) {
#ifdef DH_OVERDRAW_PREVENTION
    // Voxy clears its depth where the regular terrain stencil was set, even after
    // the handoff drew a foreground LoD. Record the depth with the material.
    OPAQUE_LOD_DEPTH = voxyUsesNearDepth ? 0.0 : gl_FragCoord.z;
#endif

    vec3 normal = vec3( uint((parameters.face >> 1) == 2), uint((parameters.face >> 1) == 0), uint((parameters.face >> 1) == 1) ) * (float(int(parameters.face) & 1) * 2.0 - 1.0);
    normal = normalize(normal);
    vec3 viewNormal = worldToView(normal);
    // An overlap pixel retains Embeddium's depth. Match the near G-buffer
    // layout so the lighting pass uses the same normal and shadow bias.
    vec3 flatNormal = voxyUsesNearDepth ? viewNormal : normal;
    vec3 shadingNormal = voxyUsesNearDepth ? normal : viewNormal;
    NORMAL_AND_AO = vec4(flatNormal * 0.5 + 0.5, 0.0);
#ifdef BLISS_COMPAT
    NORMAL_AND_AO = roundEven(NORMAL_AND_AO * 255.0) / 255.0;
#endif

    vec3 Albedo = parameters.sampledColour.rgb * parameters.tinting.rgb;

	#ifdef WhiteWorld
		Albedo.rgb = vec3(1.0);
	#endif
#ifdef VOXY_LOD_DEBUG_TINT
    Albedo = voxy_applyLodDebugTint(Albedo);
#endif

    // Voxy supplies texture sample centers. Bliss stores each light level on [0, 1].
    vec2 lightMaps = clamp((parameters.lightMap * 16.0 - 0.5) / 15.0, 0.0, 1.0);
    vec4 data1 = encode(shadingNormal, lightMaps);

    Albedo = clamp(Albedo,0.0,1.0);
    data1 = clamp(data1,0.0,1.0);

    int blockId = int(parameters.customId);
    float foliageMask = auramTerrainFoliageMask(blockId);
    DEFERRED_DATA.xyzw = vec4(  encodeVec2(Albedo.x, data1.x),
                                encodeVec2(Albedo.y, data1.y),
                                encodeVec2(Albedo.z, data1.z),
                                encodeVec2(data1.w, foliageMask) );

    float subSurfaceScattering = 0.0;
    #if SSS_TYPE == 1 || SSS_TYPE == 2
        subSurfaceScattering = auramTerrainScattering(blockId);
    #endif

    float emission = 0.0;
    #if EMISSIVE_TYPE == 1 || EMISSIVE_TYPE == 2
        // Match Bliss's near-terrain emission for lava through the LOD handoff.
        if (blockId == BLOCK_LAVA) emission = 0.5;
    #endif

    // colortex8 takes raw LabPBR channels. The compatibility pass packs them later.
    SPECULAR_DATA = vec4(0.0, 0.02, subSurfaceScattering, emission);
}
