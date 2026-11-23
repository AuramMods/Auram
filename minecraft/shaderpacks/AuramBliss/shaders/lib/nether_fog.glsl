#include "/lib/fog_noise.glsl"

#include "/lib/nether_smoke.glsl"

vec4 GetVolumetricFog(
	vec3 viewPosition,
	float dither,
	float dither2
){
	#ifndef TOGGLE_VL_FOG
		return vec4(0.0,0.0,0.0,1.0);
	#endif

	/// -------------  RAYMARCHING STUFF ------------- \\\

	int SAMPLECOUNT = 16;

	vec3 wpos = mat3(gbufferModelViewInverse) * viewPosition + gbufferModelViewInverse[3].xyz;
	vec3 dVWorld = (wpos-gbufferModelViewInverse[3].xyz);
	vec3 progressW = vec3(0.0);

	float maxLength = min(length(dVWorld), min(far, 12*16))/length(dVWorld);

	dVWorld *= maxLength;

	float dL = length(dVWorld);

	float expFactor = 11.0;
	
	/// -------------  COLOR/LIGHTING STUFF ------------- \\\

	vec3 color = vec3(0.0);
	float absorbance = 1.0;

	vec3 hazeColor = normalize(gl_Fog.color.rgb);

	#if defined LPV_VL_FOG_ILLUMINATION && defined EXCLUDE_WRITE_TO_LUT
    	float TorchBrightness_autoAdjust = mix(1.0, 30.0,  clamp(exp(-10.0*exposure),0.0,1.0)) / 5.0;
	#endif

	for (int i = 0; i < SAMPLECOUNT; i++) {
		float d = (pow(expFactor, float(i+dither)/float(SAMPLECOUNT))/expFactor - 1.0/expFactor)/(1-1.0/expFactor);
		float dd = pow(expFactor, float(i+dither)/float(SAMPLECOUNT)) * log(expFactor) / float(SAMPLECOUNT)/(expFactor-1.0);
		
		progressW = gbufferModelViewInverse[3].xyz + cameraPosition + d*dVWorld;

		float densityVol = netherSmokeField(progressW, 1.0, frameTimeCounter * 10.0);
		float clearArea = 1.0 - min(max(1.0 - length(progressW - cameraPosition) / 24.0,0.0),1.0);

		//------ PLUME EFFECT
			float plumeDensity = netherSmokePlumeDensity(densityVol, progressW.y, length(progressW - cameraPosition) / far);
			plumeDensity *= NETHER_PLUME_DENSITY;
			float plumeVolumeCoeff = exp(-plumeDensity*dd*dL);

			vec3 lighting = vec3(1.0,0.4,0.2) * netherSmokePlumeLight(densityVol) * (clearArea*clearArea*0.9+0.1);

			color += (lighting - lighting * plumeVolumeCoeff) * absorbance;
			absorbance *= plumeVolumeCoeff;

		//------ HAZE EFFECT
			// dont make haze contrube to absorbance.
			float hazeDensity = 0.001;
			#ifndef ReflectedFog
				hazeDensity *= NETHER_HAZE_DENSITY;
			#endif
			float hazeVolumeCoeff = exp(-hazeDensity*dd*dL);
			
			vec3 hazeLighting = hazeColor;
			
			color += (hazeLighting - hazeLighting*hazeVolumeCoeff) * absorbance;

		//------ CEILING SMOKE EFFECT
			float ceilingSmokeDensity = netherSmokeUpperDensity(progressW.y);
			ceilingSmokeDensity *= NETHER_CEILING_SMOKE_DENSITY;
			float ceilingSmokeVolumeCoeff = exp(-ceilingSmokeDensity*dd*dL);
			
			vec3 ceilingSmoke = vec3(1.0);

			color += (ceilingSmoke - ceilingSmoke*ceilingSmokeVolumeCoeff) * (absorbance*0.5+0.5);
			absorbance *= ceilingSmokeVolumeCoeff;

		//------ LPV FOG EFFECT
			#if defined LPV_VL_FOG_ILLUMINATION && defined EXCLUDE_WRITE_TO_LUT
				color += LPV_FOG_ILLUMINATION(progressW-cameraPosition, dd, dL) * TorchBrightness_autoAdjust * absorbance;
			#endif

	}
	return vec4(color, absorbance);
}