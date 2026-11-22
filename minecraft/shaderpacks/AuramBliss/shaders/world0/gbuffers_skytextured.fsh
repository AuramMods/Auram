#version 120
#include "/lib/settings.glsl"

#if RESOURCEPACK_SKY != 0 || defined AURAM_BLISS_ASTRAL_SKY
	varying vec4 color;
	varying vec2 texcoord;
	uniform sampler2D texture;
	
	uniform int renderStage;
	#ifdef AURAM_BLISS_ASTRAL_SKY
		uniform int auramAstralConstellation;
		#include "/lib/auram_astral_sky.glsl"
	#endif

	float interleaved_gradientNoise(){
		// vec2 coord = gl_FragCoord.xy + (frameCounter%40000);
		vec2 coord = gl_FragCoord.xy ;
		// vec2 coord = gl_FragCoord.xy;
		float noise = fract( 52.9829189 * fract( (coord.x * 0.06711056) + (coord.y * 0.00583715)) );
		return noise ;
	}
#endif

void main() {

	#if RESOURCEPACK_SKY != 0 || defined AURAM_BLISS_ASTRAL_SKY
		/* RENDERTARGETS:10 */

		vec4 COLOR = texture2D(texture, texcoord.xy)*color;
		#ifdef AURAM_BLISS_ASTRAL_SKY
			if (auramAstralConstellation != 0) {
				gl_FragData[0] = vec4(COLOR.rgb / 5.0, COLOR.a);
				return;
			}
			#if RESOURCEPACK_SKY == 0
				discard;
				return;
			#endif
			if (renderStage == MC_RENDER_STAGE_MOON) COLOR.rgb *= blissLunarTint();
		#endif

		#if RESOURCEPACK_SKY == 3
			if(renderStage == 1 || renderStage == 3) { discard; return; }
		#endif

		#if RESOURCEPACK_SKY == 1
			if(renderStage == 4 || renderStage == 5) { discard; return; }
		#else
			if(renderStage == 4) COLOR.rgb *= 5.0;
		#endif

		COLOR.rgb = max(COLOR.rgb - COLOR.rgb * interleaved_gradientNoise()*0.05, 0.0);
		
		gl_FragData[0] = vec4(COLOR.rgb/5.0, COLOR.a);
	#else
		discard;
	#endif
}
