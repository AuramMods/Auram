#ifdef HQ_CLOUDS
	int maxIT_clouds = minRayMarchSteps;
	int maxIT = maxRayMarchSteps;

	const int cloudLoD = cloud_LevelOfDetail;
	const int cloudShadowLoD = cloud_ShadowLevelOfDetail;
#else
	int maxIT_clouds = minRayMarchStepsLQ;
	int maxIT = maxRayMarchStepsLQ;

	const int cloudLoD = cloud_LevelOfDetailLQ;
	const int cloudShadowLoD = cloud_ShadowLevelOfDetailLQ;
#endif

uniform int worldTime;
#define WEATHERCLOUDS
#include "/lib/climate_settings.glsl"

#if defined Daily_Weather
	flat varying vec4 dailyWeatherParams0;
	flat varying vec4 dailyWeatherParams1;
#else
	vec4 dailyWeatherParams0 = vec4(CloudLayer0_coverage, CloudLayer1_coverage, CloudLayer2_coverage, 0.0);
	vec4 dailyWeatherParams1 = vec4(CloudLayer0_density, CloudLayer1_density, CloudLayer2_density, 0.0);
#endif

#ifdef AETHER_SHADER
	const float LAYER0_minHEIGHT = AETHER_CLOUD_BASE;
	const float LAYER0_maxHEIGHT = AETHER_CLOUD_BASE + AETHER_CLOUD_THICKNESS;
	// Compress the cumulus profile into the Aether's low cloud layer.
	const float aetherCloudHeightScale = 100.0 / (LAYER0_maxHEIGHT - LAYER0_minHEIGHT);
	const float lowCloudDensityScale = AETHER_CLOUD_DENSITY;
#else
	float LAYER0_minHEIGHT = CloudLayer0_height;
	float LAYER0_maxHEIGHT = LAYER0_minHEIGHT + 100.0;
	const float lowCloudDensityScale = 1.0;
#endif

float LAYER1_width = 100.0; 
float LAYER1_minHEIGHT = max(CloudLayer1_height, LAYER0_maxHEIGHT); 
float LAYER1_maxHEIGHT = LAYER1_width + LAYER1_minHEIGHT;

float LAYER2_HEIGHT = max(CloudLayer2_height, LAYER1_maxHEIGHT); 

// float LAYER0_COVERAGE = mix(pow(dailyWeatherParams0.x*2.0,0.2), 0.9, rainStrength);
// float LAYER1_COVERAGE = mix(pow(dailyWeatherParams0.y*2.0,0.2), 0.8, rainStrength);
// float LAYER2_COVERAGE = mix(pow(dailyWeatherParams0.z*2.0,0.2), 1.3, rainStrength);

float LAYER0_COVERAGE = mix(dailyWeatherParams0.x, Rain_coverage, rainStrength);
float LAYER1_COVERAGE = mix(dailyWeatherParams0.y, 0.0, rainStrength);
// Keep texture in the high cloud layer during precipitation.
float LAYER2_COVERAGE = mix(dailyWeatherParams0.z, 0.9, rainStrength);

float LAYER0_DENSITY = mix(dailyWeatherParams1.x, 1.0, rainStrength) * lowCloudDensityScale;
// Full precipitation makes the second cumulus layer transparent.
float LAYER1_DENSITY = mix(dailyWeatherParams1.y,0.0,rainStrength);
float LAYER2_DENSITY = mix(dailyWeatherParams1.z,0.05,rainStrength);

uniform int worldDay;

float cloud_movement = (worldTime  + mod(worldDay,100)*24000.0) / 24.0 * Cloud_Speed;

//3D noise from 2d texture
float densityAtPos(in vec3 pos){
	pos /= 18.;
	pos.xz *= 0.5;
	vec3 p = floor(pos);
	vec3 f = fract(pos);
	vec2 uv =  p.xz + f.xz + p.y * vec2(0.0,193.0);
	vec2 coord =  uv / 512.0;
	
	//The y channel has an offset to avoid using two textures fetches
	vec2 xy = texture2D(noisetex, coord).yx;

	return mix(xy.r,xy.g, f.y);
}


float GetAltostratusDensity(vec3 pos){

	float large = 1.0 - texture2D(noisetex, (pos.xz + cloud_movement)/100000.).b;
	large = max(large + LAYER2_COVERAGE - 0.7, 0.0);
	
	float medium = 1.0 - texture2D(noisetex, (pos.xz - cloud_movement)/7500. + vec2(-large,1.0-large)/5.0).b;

	float shape = max(large - medium*0.4 * clamp(1.5-large,0.0,1.0),0.0);

	return shape*shape;
}

float cloudCov(int layer, in vec3 pos, vec3 samplePos, float minHeight, float maxHeight){
	float FinalCloudCoverage = 0.0;
	float coverage = 0.0;
	float Topshape = 0.0;
	float Baseshape = 0.0;

	float LAYER0_minHEIGHT_FOG = LAYER0_minHEIGHT;
	#ifdef AETHER_SHADER
		float LAYER0_maxHEIGHT_FOG = maxHeight;
	#else
		float LAYER0_maxHEIGHT_FOG = LAYER0_maxHEIGHT;
	#endif

	float LAYER1_minHEIGHT_FOG = max(CloudLayer1_height, LAYER0_maxHEIGHT); 
	float LAYER1_maxHEIGHT_FOG = 100 + LAYER1_minHEIGHT_FOG;
	LAYER1_minHEIGHT_FOG = LAYER1_minHEIGHT;
	LAYER1_maxHEIGHT_FOG = LAYER1_maxHEIGHT;


	vec2 SampleCoords0 = vec2(0.0); vec2 SampleCoords1 = vec2(0.0);

	float CloudSmall = 0.0;
	if(layer == 0){
		SampleCoords0 = (samplePos.xz + cloud_movement) / 5000 ;
		SampleCoords1 = (samplePos.xz - cloud_movement) / 500 ;
		CloudSmall = texture2D(noisetex, SampleCoords1 ).r;
	}

	if(layer == 1){
		SampleCoords0 = -( (samplePos.zx + cloud_movement*2) / 10000);
		SampleCoords1 = -( (samplePos.zx - cloud_movement*2) / 2500);
		CloudSmall = texture2D(noisetex, SampleCoords1 ).b;
	}

	if(layer == -1){
		float otherlayer = max(pos.y - (LAYER0_minHEIGHT_FOG+99.5), 0.0) > 0 ? 0.0 : 1.0;
		if(otherlayer > 0.0){
			SampleCoords0 = (samplePos.xz + cloud_movement) / 5000 ;
			SampleCoords1 = (samplePos.xz - cloud_movement) / 500 ;
			CloudSmall = texture2D(noisetex, SampleCoords1 ).r;
		}else{
			SampleCoords0 = -( (samplePos.zx + cloud_movement*2) / 10000);
			SampleCoords1 = -( (samplePos.zx - cloud_movement*2) / 2500);
			CloudSmall = texture2D(noisetex, SampleCoords1 ).b;
		}
	}

	float CloudLarge = texture2D(noisetex, SampleCoords0).b;

	if(layer == 0){
		coverage = abs(CloudLarge*2.0 - 1.2)*0.5 - (1.0-CloudSmall);

		float layer0 = min(min(coverage + LAYER0_COVERAGE, clamp(LAYER0_maxHEIGHT_FOG - pos.y,0,1)), 1.0 - clamp(LAYER0_minHEIGHT_FOG - pos.y,0,1));

		Topshape = max(pos.y - (LAYER0_maxHEIGHT_FOG - 75),0.0) / 200.0;
		Topshape += max(pos.y - (LAYER0_maxHEIGHT_FOG - 10),0.0) / 15.0;
		Baseshape = max(LAYER0_minHEIGHT_FOG + 12.5 - pos.y, 0.0) / 50.0;

		FinalCloudCoverage = max(layer0 - Topshape - Baseshape * (1.0-rainStrength),0.0);
	}

	if(layer == 1){
		
		coverage = abs(CloudLarge-0.8) - CloudSmall;

		float layer1 = min(min(coverage + LAYER1_COVERAGE - 0.5,clamp(LAYER1_maxHEIGHT_FOG - pos.y,0,1)), 1.0 - clamp(LAYER1_minHEIGHT_FOG - pos.y,0,1));

		Topshape = max(pos.y - (LAYER1_maxHEIGHT_FOG - 75),0.0) / 200.0;
		Topshape += max(pos.y - (LAYER1_maxHEIGHT_FOG - 10), 0.0) / 15.0;
		Baseshape = max(LAYER1_minHEIGHT_FOG + 15.5 - pos.y, 0.0) / 50.0;

		FinalCloudCoverage = max(layer1 - Topshape*Topshape - Baseshape * (1.0-rainStrength), 0.0);
	}


	if(layer == -1){
	
		#ifdef CloudLayer0 
			float layer0_coverage =  abs(CloudLarge*2.0 - 1.2)*0.5 - (1.0-CloudSmall);
			float layer0 = min(min(layer0_coverage + LAYER0_COVERAGE, clamp(LAYER0_maxHEIGHT_FOG - pos.y,0,1)), 1.0 - clamp(LAYER0_minHEIGHT_FOG - pos.y,0,1));

			Topshape = max(pos.y - (LAYER0_maxHEIGHT_FOG - 75),0.0) / 200.0;
			Topshape += max(pos.y - (LAYER0_maxHEIGHT_FOG - 10),0.0) / 15.0;
			Baseshape = max(LAYER0_minHEIGHT_FOG + 12.5 - pos.y, 0.0) / 50.0;

			FinalCloudCoverage = max(layer0 - Topshape - Baseshape * (1.0-rainStrength),0.0);
		#endif
		
		#ifdef CloudLayer1
			float layer1_coverage = abs(CloudLarge-0.8) - CloudSmall;
			float layer1 = min(min(layer1_coverage + LAYER1_COVERAGE - 0.5,clamp(LAYER1_maxHEIGHT_FOG - pos.y,0,1)), 1.0 - clamp(LAYER1_minHEIGHT_FOG - pos.y,0,1));

			Topshape = max(pos.y - (LAYER1_maxHEIGHT_FOG - 75), 0.0) / 200;
			Topshape += max(pos.y - (LAYER1_maxHEIGHT_FOG - 10 ), 0.0) / 50;
			Baseshape = max(LAYER1_minHEIGHT_FOG + 12.5 - pos.y, 0.0) / 50.0;

			FinalCloudCoverage += max(layer1 - Topshape*Topshape - Baseshape * (1.0-rainStrength), 0.0);
		#endif
	}

	return FinalCloudCoverage;
}

//Erode cloud with 3d Perlin-worley noise, actual cloud value
float cloudVol(int layer, in vec3 pos, in vec3 samplePos, in float cov, in int LoD, float minHeight, float maxHeight){
	
	// float curvature = 1-exp(-25*pow(clamp(1.0 - length(pos - cameraPosition)/(32*80),0.0,1.0),2));
	// curvature = clamp(1.0 - length(pos - cameraPosition)/(32*128),0.0,1.0);

	float otherlayer = max(pos.y - (CloudLayer0_height+99.5), 0.0) > 0 ? 0.0 : 1.0;
	float upperPlane = otherlayer;

	float noise = 0.0 ;
	float totalWeights = 0.0;
	float pw =  log(fbmPower1);
	float pw2 = log(fbmPower2);

	samplePos.xz -= cloud_movement/4;

	samplePos.xz += pow( max(pos.y - (minHeight+20), 0.0) / 20.0,1.50) ;

	noise += (1.0-densityAtPos(samplePos * mix(100.0,200.0,upperPlane)) ) * sqrt(1.0-cov);

	if (LoD > 0){
		noise += abs( densityAtPos(samplePos * mix(450.0,600.0,upperPlane) ) - (1.0-clamp(((maxHeight - pos.y) / 100.0),0.0,1.0))) * 0.75 * (1.0-cov);
	}

	noise = noise*noise;
	float cloud = max(cov - noise*noise*fbmAmount,0.0);

	return cloud;
}

float GetCumulusDensity(int layer, in vec3 pos, in int LoD, float minHeight, float maxHeight){

	#ifdef AETHER_SHADER
		if (pos.y <= LAYER0_minHEIGHT || pos.y >= LAYER0_maxHEIGHT) return 0.0;
		pos.y = LAYER0_minHEIGHT + (pos.y - LAYER0_minHEIGHT) * aetherCloudHeightScale;
		maxHeight = LAYER0_minHEIGHT + 100.0;
	#endif
	vec3 samplePos = pos * vec3(1.0, 1.0 / 48.0, 1.0) / 4.0;
	
	float coverageSP = cloudCov(layer, pos,samplePos, minHeight, maxHeight);

	// return coverageSP;
	if (coverageSP > 0.001) {
		if (LoD < 0) return max(coverageSP - 0.27*fbmAmount,0.0);
		return cloudVol(layer, pos,samplePos,coverageSP,LoD	,minHeight, maxHeight) ;
	} else return 0.0;
}


#ifndef CLOUDSHADOWSONLY
uniform sampler2D colortex4; //Skybox

//Mie phase function
float phaseg(float x, float g){
    float gg = g * g;
    return (gg * -0.25 + 0.25) * pow(-2.0 * (g * x) + (gg + 1.0), -1.5) / 3.14;
}

vec3 DoCloudLighting(
	float density,
	
	vec3 skyLightCol,
	float skyScatter,

	float sunShadows,
	vec3 sunScatter,
	vec3 sunMultiScatter,
	float distantfog
){
	float powder = 1.0 - exp(-10.0 * density);
	vec3 directLight = sunScatter * exp(-10.0 * sunShadows) + sunMultiScatter * exp(-3.0 * sunShadows) * powder;

	vec3 indirectLight = skyLightCol * mix(1.0,  2.0 * (1.0 - sqrt((skyScatter*skyScatter*skyScatter)*density)) , pow(distantfog,1.0 - rainStrength*0.5));
	
	// return directLight;
	// #ifndef TEST
	// return indirectLight;
	// #endif
	return directLight + indirectLight;
}

const float maxCloudTraceDistance = 16384.0;

// Intersect the forward ray with the cloud layer before dividing it into steps.
vec2 cloudRayInterval(float rayY, float minHeight, float maxHeight, float traceDistance) {
	float origin = cameraPosition.y;
	if (abs(rayY) < 1e-6) {
		return origin >= minHeight && origin <= maxHeight ? vec2(0.0, traceDistance) : vec2(0.0);
	}

	vec2 intersections = (vec2(minHeight, maxHeight) - origin) / rayY;
	return vec2(max(min(intersections.x, intersections.y), 0.0),
		min(max(intersections.x, intersections.y), traceDistance));
}

vec4 renderLayer(
	int layer,
	vec3 rayDirection,
	float traceDistance,
	vec2 dither,
	int quality,
	float minHeight,
	float maxHeight,
	vec3 dV_Sun,
	float cloudDensity,
	vec3 skyLightCol,
	vec3 sunScatter,
	vec3 sunMultiScatter,
	float distantfog,
	bool notVisible,
	out float layerDistance
){
	layerDistance = traceDistance;
	vec3 COLOR = vec3(0.0);
	float TOTAL_EXTINCTION = 1.0;
	if (notVisible || cloudDensity == 0.0) return vec4(COLOR, TOTAL_EXTINCTION);

	// Altostratus uses one sample across its five-block thickness.
	float halfThickness = layer == 2 ? 2.5 : 0.0;
	vec2 interval = cloudRayInterval(rayDirection.y, minHeight - halfThickness, maxHeight + halfThickness, maxCloudTraceDistance);
	float rayEnd = min(interval.y, traceDistance);
	float rayLength = rayEnd - interval.x;
	if (rayLength <= 0.0) return vec4(COLOR, TOTAL_EXTINCTION);

	int steps = layer == 2 ? 1 : quality;
	float mult = rayLength / float(steps);
	vec3 rayProgress = cameraPosition + rayDirection * interval.x + (rayDirection * mult) * dither.y;

	if (layer == 2) {
		float altostratus = GetAltostratusDensity(rayProgress);

		float AltoWithDensity = altostratus * cloudDensity;
		
		if(altostratus > 1e-5){
			float directLight = 0.0;
			for (int j = 0; j < 2; j++){
				
				// lower the step size as the sun gets higher in the sky
				vec3 shadowSamplePos_high = rayProgress + dV_Sun * (1.0 + j * dither.x) / (pow(abs(dV_Sun.y*0.5),3.0) * 0.995 + 0.005);

				// lower density as the sun gets higher in the sky to simulate.... multiscattering or something idk it looks better this way
				directLight += GetAltostratusDensity(shadowSamplePos_high) * cloudDensity * (1.0-abs(dV_Sun.y));
			}

			vec3 lighting = DoCloudLighting(AltoWithDensity, skyLightCol, 0.5, directLight, sunScatter, sunMultiScatter, distantfog);

			// Fade the distant cloud into the sky before the ray limit can produce a hard edge.
			float fadeStart = mix(0.5, 0.0, rainStrength) * maxCloudTraceDistance;
			float visibility = 1.0 - smoothstep(fadeStart, maxCloudTraceDistance, interval.x);
			float opacity = (1.0 - exp(-mult * AltoWithDensity)) * visibility;
			layerDistance = interval.x + mult * dither.y;
			return vec4(max(lighting * opacity, 0.0), 1.0 - opacity);
		}
		return vec4(COLOR, TOTAL_EXTINCTION);
	}

	// Take closer samples on long rays that enter the layer near the camera.
	// Use the unclipped layer distance to keep those samples aligned over terrain and sky.
	float openLength = interval.y - interval.x;
	float nearBias = (1.0 - smoothstep(0.0, 64.0, interval.x)) * smoothstep(128.0, 512.0, openLength);
	float sampleRange = mix(rayLength, openLength, nearBias);
	float inverseSteps = 1.0 / float(steps);
	float firstBoundary = interval.x + mix(inverseSteps, inverseSteps * inverseSteps * inverseSteps, nearBias) * sampleRange;
	rayProgress = cameraPosition + rayDirection * mix(interval.x, min(firstBoundary, rayEnd), dither.y);

	float skylightOcclusion = 1.0;
	#if defined CloudLayer1 && defined CloudLayer0
		if (layer == 0 && LAYER1_DENSITY > 0.0) {
			vec3 upperSample = rayProgress + vec3(0.0, 1.0, 0.0) * max((LAYER1_minHEIGHT + 70.0 * dither.x) - rayProgress.y, 0.0);
			float upperLayerOcclusion = GetCumulusDensity(1, upperSample, 0, LAYER1_minHEIGHT, LAYER1_maxHEIGHT);
			skylightOcclusion = mix(1.0, (1.0 - LAYER1_DENSITY)*0.8 + 0.2, (1.0 - exp2(-5.0 * (upperLayerOcclusion*upperLayerOcclusion))) * distantfog);
		}
	#endif

	float depthMoment = 0.0;
	float stepStart = interval.x;
	for (int i = 0; i < steps && stepStart < rayEnd; i++) {
		float t = float(i + 1) * inverseSteps;
		float boundary = interval.x + mix(t, t * t * t, nearBias) * sampleRange;
		float stepEnd = i == steps - 1 ? rayEnd : min(rayEnd, boundary);
		float stepLength = stepEnd - stepStart;
		float sampleDistance = stepStart + stepLength * dither.y;
		rayProgress = cameraPosition + rayDirection * sampleDistance;
		float cumulus = GetCumulusDensity(layer, rayProgress, 1, minHeight, maxHeight);
		float heightInLayer = (rayProgress.y - minHeight) / (maxHeight - minHeight);
		float fadedDensity = cloudDensity * pow(clamp(heightInLayer * 4.0, 0.0, 1.0), 2.0);
		float CumulusWithDensity = cloudDensity * cumulus;

		
		if(CumulusWithDensity > 1e-5 ){ // make sure no work is done on pixels with no densities
			float muE =	cumulus * fadedDensity;

			float directLight = 0.0;
			for (int j=0; j < 3; j++){
				float shadowStep = 20.0 + j * (20.0 + dither.x * 20.0);
				#ifdef AETHER_SHADER
					shadowStep /= aetherCloudHeightScale;
				#endif
				vec3 shadowSamplePos = rayProgress + dV_Sun * shadowStep;
				directLight += GetCumulusDensity(layer, shadowSamplePos, 0, minHeight, maxHeight) * cloudDensity;
			}

			/// shadows cast from one layer to another
			/// large cumulus -> small cumulus
			#if defined CloudLayer1 && defined CloudLayer0
				if (layer == 0 && LAYER1_DENSITY > 0.0) directLight += LAYER1_DENSITY * 2.0 * GetCumulusDensity(1, rayProgress + dV_Sun/abs(dV_Sun.y) * max((LAYER1_minHEIGHT+70*dither.x) - rayProgress.y,0.0), 0, LAYER1_minHEIGHT, LAYER1_maxHEIGHT);
			#endif
			// altostratus -> cumulus
			#ifdef CloudLayer2
				vec3 HighAlt_shadowPos = rayProgress + dV_Sun/abs(dV_Sun.y) * max(LAYER2_HEIGHT - rayProgress.y,0.0);
				float HighAlt_shadow = GetAltostratusDensity(HighAlt_shadowPos) * CloudLayer2_density * (1.0-abs(dV_Sun.y));
				directLight += HighAlt_shadow;
			#endif

			float skyScatter = clamp(1.0 - heightInLayer, 0.0, 1.0);
			vec3 lighting = DoCloudLighting(CumulusWithDensity, skyLightCol * skylightOcclusion, skyScatter, directLight, sunScatter, sunMultiScatter, distantfog);

			float transmittance = exp(-stepLength * muE);
			float opacity = (1.0 - transmittance) * TOTAL_EXTINCTION;
			COLOR += max(lighting * opacity, 0.0);
			depthMoment += sampleDistance * opacity;
			TOTAL_EXTINCTION *= transmittance;

			if (TOTAL_EXTINCTION < 1e-5) break;
		}
		stepStart = stepEnd;
	}

	if (TOTAL_EXTINCTION < 1.0) layerDistance = depthMoment / (1.0 - TOTAL_EXTINCTION);
	return vec4(COLOR, TOTAL_EXTINCTION);
}

vec4 renderClouds(
	vec3 viewDirection,
	float surfaceDistance,
	vec2 Dither,
	vec3 LightColor,
	vec3 SkyColor,
	out float cloudDistance
){
	cloudDistance = maxCloudTraceDistance;
	#ifndef VOLUMETRIC_CLOUDS
		return vec4(0.0,0.0,0.0,1.0);
	#endif

	float total_extinction = 1.0;
	vec4 scattering = vec4(0.0);

	float heightRelativeToClouds = clamp(1.0 - max(cameraPosition.y - LAYER0_minHEIGHT,0.0) / 100.0 ,0.0,1.0);

//////////////////////////////////////////
////// Raymarching stuff 
//////////////////////////////////////////
	vec3 viewPos = mat3(gbufferModelViewInverse) * viewDirection;
	maxIT_clouds = max(1, int(min(float(maxIT_clouds) / sqrt(exp2(viewPos.y)), float(maxIT))));

	#ifdef AETHER_SHADER
		// Keep the low cloud slab at its real world height even near the void.
		vec3 dV_view = viewPos;
	#else
		// Retain cloud curvature while keeping ray distances in world units.
		vec3 dV_view = normalize(viewPos + vec3(0.0, 0.025 * heightRelativeToClouds, 0.0));
	#endif
	float traceDistance = maxCloudTraceDistance;
	#ifdef CLOUDS_INTERSECT_TERRAIN
		if (surfaceDistance >= 0.0) traceDistance = min(traceDistance, surfaceDistance);
	#endif

//////////////////////////////////////////
////// lighting stuff 
//////////////////////////////////////////

	// sunVec always points toward the sun, regardless of the pass's WsunVec convention.
	vec3 worldSunDirection = mat3(gbufferModelViewInverse) * sunVec;
	vec3 dV_Sun = worldSunDirection * (sunElevation > 1e-5 ? 1.0 : -1.0);
	float SdotV = clamp(dot(dV_Sun, viewPos), -1.0, 1.0);

	float mieDay = phaseg(SdotV, 0.85) + phaseg(SdotV, 0.75);
	float mieDayMulti = (phaseg(SdotV, 0.35) + phaseg(-SdotV, 0.35) * 0.5) ;

	vec3 directScattering = LightColor * mieDay * 3.14 ;
	vec3 directMultiScattering = LightColor * mieDayMulti * 3.14 * 2.0;

	// Fade out the ground-view horizon lighting as the camera enters the cloud.
	float horizonFadeStrength = heightRelativeToClouds;
	#ifdef CloudLayer0
		float heightAboveCloudBase = cameraPosition.y - LAYER0_minHEIGHT;
		horizonFadeStrength = 1.0 - smoothstep(-8.0, 12.0, heightAboveCloudBase);
	#endif
	vec3 approxdistance = dV_view;
	#ifdef SKY_GROUND
		float distantfog = mix(1.0, max(1.0 - clamp(exp2(pow(abs(approxdistance.y),mix(1.5, 4.0, rainStrength)) * -mix(100.0, 35.0, rainStrength)),0.0,1.0),0.0), horizonFadeStrength);
	#else
		float distantfog = 1.0;
		float distantfog2 = mix(1.0, max(1.0 - clamp(exp(pow(abs(approxdistance.y),1.5) * -35.0),0.0,1.0),0.0), horizonFadeStrength);
	#endif
	
	// terrible fake rayleigh scattering
	vec3 rC = vec3(sky_coefficientRayleighR*1e-6, sky_coefficientRayleighG*1e-5, sky_coefficientRayleighB*1e-5)*3.0;
	float atmosphere =  exp(abs(approxdistance.y) * -5.0);
	vec3 scatter = distantfog * exp(-10000.0 * rC * atmosphere);

	directScattering *= scatter;
	directMultiScattering *= scatter;

	SkyColor *= mix(1.0* Sky_Brightness, 1.0-pow(1.0-clamp(WsunVec.y,0.0,1.0),5.0) * 0.75 + 0.25, distantfog);

//////////////////////////////////////////
////// render Cloud layers and do blending orders
//////////////////////////////////////////

	// first cloud layer
	float MinHeight = LAYER0_minHEIGHT; 
	float MaxHeight = LAYER0_maxHEIGHT;

	float MinHeight1 = LAYER1_minHEIGHT;
	float MaxHeight1 = LAYER1_maxHEIGHT;

	float Height2 = LAYER2_HEIGHT;

	// int above_Layer0 = int(clamp(cameraPosition.y - MaxHeight,0.0,1.0));
	int below_Layer0 = int(clamp(MaxHeight - cameraPosition.y,0.0,1.0));
	int above_Layer1 = int(clamp(MaxHeight1 - cameraPosition.y,0.0,1.0));
	bool below_Layer1 = clamp(cameraPosition.y - MinHeight1,0.0,1.0) < 1.0;
	bool below_Layer2 = clamp(cameraPosition.y - Height2,0.0,1.0) < 1.0;
	// bool layer1_below_layer0 = MinHeight1 < MinHeight;
	
	bool altoNotVisible = false;
	

	#ifdef CloudLayer0
		float distance0;
		vec4 layer0 = renderLayer(0, dV_view, traceDistance, Dither, maxIT_clouds, MinHeight, MaxHeight, dV_Sun, LAYER0_DENSITY, SkyColor, directScattering, directMultiScattering, distantfog, false, distance0);
		vec4 scattering0 = vec4(layer0.rgb, distance0 * (1.0 - layer0.a));
		total_extinction *= layer0.a;

		// stop overdraw.
		bool notVisible = layer0.a < 1e-5 && below_Layer1;
		altoNotVisible = notVisible;
	#else
		// stop overdraw.
		bool notVisible = false;
	#endif

	#ifdef CloudLayer1
		float distance1;
		vec4 layer1 = renderLayer(1, dV_view, traceDistance, Dither, maxIT_clouds, MinHeight1, MaxHeight1, dV_Sun, LAYER1_DENSITY, SkyColor, directScattering, directMultiScattering, distantfog, notVisible, distance1);
		vec4 scattering1 = vec4(layer1.rgb, distance1 * (1.0 - layer1.a));
		total_extinction *= layer1.a;

		// stop overdraw.
		altoNotVisible = (layer1.a < 1e-5  || notVisible) && below_Layer1;	
	#endif

	#ifdef CloudLayer2
		float distance2;
		vec4 layer2 = renderLayer(2, dV_view, traceDistance, Dither, maxIT_clouds, Height2, Height2, dV_Sun, LAYER2_DENSITY, SkyColor, directScattering * (1.0 + rainStrength*3), directMultiScattering * (1.0 + rainStrength*3), distantfog, altoNotVisible, distance2);
		vec4 scattering2 = vec4(layer2.rgb, distance2 * (1.0 - layer2.a));
		total_extinction *= layer2.a;
	#endif
	
	/// i know this looks confusing
	/// it is changing blending order based on the players position relative to the clouds.
	/// to keep it simple for myself, it all revolves around layer0, the lowest cloud layer.
	/// for layer1, swap between back to front and front to back blending if you are above or below layer0
	/// for layer2, swap between back to front and front to back blending if you are above or below layer1
	

	/// blend the altostratus clouds first, so it is BEHIND all the cumulus clouds, if the player postion is below the cumulus clouds.
	/// handle the case if one of the cloud layers is disabled.
	#if !defined CloudLayer1 && defined CloudLayer2
		if(below_Layer2) scattering = scattering * layer2.a + scattering2;
	#endif
	#if defined CloudLayer1 && defined CloudLayer2 
		if(below_Layer2) scattering1 = scattering2 * layer1.a + scattering1;
	#endif

	/// blend the cumulus clouds together. swap the blending order from (BACK TO FRONT -> FRONT TO BACK) depending on the player position relative to the lowest cloud layer.
	#if defined CloudLayer0 && defined CloudLayer1
		scattering = mix(scattering0, scattering1, float(below_Layer0));
		scattering = mix(scattering * layer1.a + scattering1, scattering * layer0.a + scattering0, float(below_Layer0));
	#endif

	/// handle the case of one of the cloud layers being disabled.
	#if defined CloudLayer0 && !defined CloudLayer1
		scattering = scattering * layer0.a + scattering0;
	#endif
	#if !defined CloudLayer0 && defined CloudLayer1
		scattering = scattering * layer1.a + scattering1;
	#endif

	/// blend the altostratus clouds last, so it is IN FRONT of all the cumulus clouds when the player position is above them.
	#ifdef CloudLayer2
		if(!below_Layer2) scattering = scattering * layer2.a + scattering2;
	#endif

	// The opacity-weighted distance places the accumulated cloud layer between foreground and background fog.
	if (total_extinction < 1.0) cloudDistance = scattering.a / (1.0 - total_extinction);

	#ifndef SKY_GROUND
		
		// return mix(fogcolor, vec4(scattering.rgb, total_extinction), clamp(distantfog2,0.0,1.0));
		return mix(vec4(vec3(0.0),1.0), vec4(scattering.rgb, total_extinction), clamp(distantfog2,0.0,1.0));
	#else
		return vec4(scattering.rgb, total_extinction);
	#endif
	
}

#endif

#if defined CLOUDS_SHADOWS && defined CloudLayer0
float getLowCloudShadowDensity(vec3 worldPos, vec3 lightDirection) {
	#ifdef AETHER_SHADER
		if (lightDirection.y <= 1e-5 || worldPos.y >= LAYER0_maxHEIGHT) return 0.0;
		float shadowHeight = mix(LAYER0_minHEIGHT, LAYER0_maxHEIGHT, 0.3);
		vec3 samplePos = worldPos + lightDirection * (max(shadowHeight - worldPos.y, 0.0) / lightDirection.y);
	#else
		vec3 samplePos = worldPos + (lightDirection / abs(lightDirection.y)) * max(LAYER0_minHEIGHT + 30.0 - worldPos.y, 0.0);
	#endif
	return GetCumulusDensity(0, samplePos, 0, LAYER0_minHEIGHT, LAYER0_maxHEIGHT) * LAYER0_DENSITY;
}
#endif

float GetCloudShadow(vec3 feetPlayerPos){
#ifdef CLOUDS_SHADOWS
	vec3 playerPos = feetPlayerPos + cameraPosition;

	float shadow = 0.0;

	// assume a flat layer of cloud, and stretch the sampled density along the sunvector, starting from some vertical layer in the cloud.
	#ifdef CloudLayer0
		shadow += getLowCloudShadowDensity(playerPos, WsunVec);
	#endif
	#ifdef CloudLayer1
		if (LAYER1_DENSITY > 0.0) {
			vec3 higherShadowStart = playerPos + (WsunVec / max(abs(WsunVec.y),0.0)) * max((CloudLayer1_height + 50) - playerPos.y,0.0) ;
			shadow += GetCumulusDensity(1, higherShadowStart, 0, CloudLayer1_height, CloudLayer1_height+100)*LAYER1_DENSITY;
		}
	#endif
	#ifdef CloudLayer2 
		vec3 highShadowStart = playerPos + (WsunVec / max(abs(WsunVec.y),0.0)) * max(CloudLayer2_height - playerPos.y,0.0);
		shadow += GetAltostratusDensity(highShadowStart) * CloudLayer2_density * (1.0-abs(WsunVec.y));
	#endif

	shadow = clamp(shadow,0.0,1.0);

	shadow = exp2((shadow*shadow) * -100.0);

	return mix(1.0, shadow, CLOUD_SHADOW_STRENGTH);
	
#else
	return 1.0;
#endif
}


float GetCloudShadow_VLFOG(vec3 WorldPos, vec3 WorldSpace_sunVec){
#ifdef CLOUDS_SHADOWS

	float shadow = 0.0;

	#ifdef CloudLayer0
		shadow += getLowCloudShadowDensity(WorldPos, WorldSpace_sunVec);
	#endif
	#ifdef CloudLayer1
		if (LAYER1_DENSITY > 0.0) {
			vec3 higherShadowStart = WorldPos + (WorldSpace_sunVec / max(abs(WorldSpace_sunVec.y),0.0)) * max((CloudLayer1_height + 30) - WorldPos.y,0.0)  ;
			shadow += max(GetCumulusDensity(1,higherShadowStart, 0, CloudLayer1_height,CloudLayer1_height+100) ,0.0)*LAYER1_DENSITY;
		}
	#endif
	#ifdef CloudLayer2 
		vec3 highShadowStart = WorldPos + (WorldSpace_sunVec / max(abs(WorldSpace_sunVec.y),0.0)) * max(CloudLayer2_height - WorldPos.y,0.0);
		shadow += GetAltostratusDensity(highShadowStart)*LAYER2_DENSITY * (1.0-abs(WorldSpace_sunVec.y));
	#endif

	shadow = clamp(shadow,0.0,1.0);

	shadow = exp((shadow*shadow) * -100.0);

	return mix(1.0, shadow, CLOUD_SHADOW_STRENGTH);

#else
	return 1.0;
#endif
}