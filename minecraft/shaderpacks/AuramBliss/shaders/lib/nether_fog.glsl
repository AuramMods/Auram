#include "/lib/fog_noise.glsl"
#include "/lib/vapor.glsl"

#include "/lib/nether_smoke.glsl"

vec4 GetVolumetricFog(
	vec3 viewPosition,
	float surfaceDistance,
	float dither,
	float dither2
){
	#ifndef TOGGLE_VL_FOG
		return vec4(0.0,0.0,0.0,1.0);
	#endif

	/// -------------  RAYMARCHING STUFF ------------- \\\

	const int sampleCount = 16;
	const float expFactor = 11.0;
	vec3 origin = gbufferModelViewInverse[3].xyz;
	vec3 direction = normalize(mat3(gbufferModelViewInverse) * viewPosition);
	float fogEnd = min(length(viewPosition), min(far, 192.0));
	vec3 smokeCenter = vaporCenter();
	vec3 cameraFromSmokeCenter = origin - smokeCenter;
	vec2 smokeInterval = vaporCubeInterval(origin, direction, smokeCenter, surfaceDistance);
	float marchEnd = max(fogEnd, smokeInterval.y);
	if (marchEnd <= 0.0) return vec4(0.0, 0.0, 0.0, 1.0);
	float stepStart = 0.0;
	float stepRatio = pow(expFactor, 1.0 / float(sampleCount));
	float stepScale = marchEnd / (expFactor - 1.0);

	/// -------------  COLOR/LIGHTING STUFF ------------- \\\

	vec3 color = vec3(0.0);
	float absorbance = 1.0;

	vec3 hazeColor = normalize(gl_Fog.color.rgb);

	#if defined LPV_VL_FOG_ILLUMINATION && defined EXCLUDE_WRITE_TO_LUT
    	float TorchBrightness_autoAdjust = mix(1.0, 30.0,  clamp(exp(-10.0*exposure),0.0,1.0)) / 5.0;
	#endif

	for (int i = 0; i < sampleCount; i++) {
		float stepEnd = i == sampleCount - 1 ? marchEnd : (stepStart + stepScale) * stepRatio - stepScale;
		float smokeStart = max(stepStart, smokeInterval.x);
		float smokeLength = min(stepEnd, smokeInterval.y) - smokeStart;
		float fogLength = max(min(stepEnd, fogEnd) - stepStart, 0.0);
		float ceilingTransmission = 1.0;
		if (smokeLength > 0.0) {
			float distance = smokeStart + smokeLength * dither;
			vec3 progressW = origin + cameraPosition + distance * direction;
			float smokeWeight = smokeLength * vaporEdgeFade(cameraFromSmokeCenter + distance * direction);

			float densityVol = netherSmokeField(progressW, 1.0, frameTimeCounter * 10.0);
			float plumeDensity = netherSmokePlumeDensity(densityVol, progressW.y) * NETHER_PLUME_DENSITY;
			float plumeTransmission = exp(-plumeDensity * smokeWeight);
			float clearArea = clamp(distance / 24.0, 0.0, 1.0);
			vec3 lighting = vec3(1.0, 0.4, 0.2) * netherSmokePlumeLight(densityVol) * (clearArea * clearArea * 0.9 + 0.1);
			color += lighting * (1.0 - plumeTransmission) * absorbance;
			absorbance *= plumeTransmission;

			float ceilingSmokeDensity = netherSmokeUpperDensity(progressW.y) * NETHER_CEILING_SMOKE_DENSITY;
			ceilingTransmission = exp(-ceilingSmokeDensity * smokeWeight);
		}
		if (fogLength > 0.0) {
			float hazeDensity = 0.001;
			#ifndef ReflectedFog
				hazeDensity *= NETHER_HAZE_DENSITY;
			#endif
			color += hazeColor * (1.0 - exp(-hazeDensity * fogLength)) * absorbance;
		}
		color += vec3(1.0 - ceilingTransmission) * (absorbance * 0.5 + 0.5);
		absorbance *= ceilingTransmission;
		#if defined LPV_VL_FOG_ILLUMINATION && defined EXCLUDE_WRITE_TO_LUT
			if (fogLength > 0.0) {
				vec3 fogPosition = origin + (stepStart + fogLength * dither) * direction;
				color += LPV_FOG_ILLUMINATION(fogPosition, fogLength, 1.0) * TorchBrightness_autoAdjust * absorbance;
			}
		#endif
		stepStart = stepEnd;
	}

	#ifdef AURAM_RENDER_VAPOR
		if (auramVaporActive) {
			vec2 interval = vaporRayInterval(origin, direction, surfaceDistance);
			float vaporTransmission = 1.0;
			VaporScattering vapor = traceVapor(origin, direction, interval, 0.0, interval.y, vec3(0.034, 0.012, 0.008), vaporTransmission);
			color = color * vapor.transmission + vapor.ambient + vapor.blockLight;
			absorbance *= vapor.transmission;
		}
	#endif
	return vec4(color, absorbance);
}