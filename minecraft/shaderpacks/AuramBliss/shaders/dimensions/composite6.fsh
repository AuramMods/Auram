#include "/lib/settings.glsl"
#include "/lib/res_params.glsl"

uniform sampler2D depthtex0;
uniform sampler2D colortex1;
uniform sampler2D colortex5;
uniform vec2 texelSize;
uniform float viewWidth;
uniform float viewHeight;

#ifdef RF_FLOW_STREAKS
    #ifdef AURAM_RF_BACKEND
        uniform mat4 gbufferProjectionInverse;
        uniform mat4 gbufferModelViewInverse;
        uniform float frameTimeCounter;
        #ifdef DISTANT_HORIZONS
            uniform sampler2D dhDepthTex;
            uniform float dhNearPlane;
            uniform float dhFarPlane;
        #endif
        #include "/lib/rf_streaks.glsl"
    #endif
#endif

vec2 decodeVec2(float a){
    const vec2 constant1 = 65535. / vec2( 256., 65536.);
    const float constant2 = 256. / 255.;
    return fract( a * constant1 ) * constant2 ;
}

//////////////////////////////VOID MAIN//////////////////////////////
//////////////////////////////VOID MAIN//////////////////////////////
//////////////////////////////VOID MAIN//////////////////////////////
//////////////////////////////VOID MAIN//////////////////////////////
//////////////////////////////VOID MAIN//////////////////////////////

void main() {

/* DRAWBUFFERS:3 */

vec2 resScale = max(vec2(viewWidth,viewHeight),vec2(1920.0,1080.))/vec2(1920.,1080.);
vec2 quarterResTC = gl_FragCoord.xy*2.0*resScale*texelSize;

// vec2 texcoord = (gl_FragCoord.xy*2.0*resScale*texelSize) * RENDER_SCALE; 

// bool hand = abs(decodeVec2(texture2D(colortex1,texcoord).w).y-0.75) < 0.01 && texture2D(depthtex0,texcoord).x < 1.0;

		//0.5
		gl_FragData[0] = texture2D(colortex5,quarterResTC-1.0*vec2(texelSize.x,texelSize.y))/4.*0.5;
		gl_FragData[0] += texture2D(colortex5,quarterResTC+1.0*vec2(texelSize.x,texelSize.y))/4.*0.5;
		gl_FragData[0] += texture2D(colortex5,quarterResTC+vec2(-1.0*texelSize.x,1.0*texelSize.y))/4.*0.5;
		gl_FragData[0] += texture2D(colortex5,quarterResTC+vec2(1.0*texelSize.x,-1.0*texelSize.y))/4.*0.5;

		//0.25
		gl_FragData[0] += texture2D(colortex5,quarterResTC-2.0*vec2(texelSize.x,0.0))/2.*0.125;
		gl_FragData[0] += texture2D(colortex5,quarterResTC+2.0*vec2(0.0,texelSize.y))/2.*0.125;
		gl_FragData[0] += texture2D(colortex5,quarterResTC+2.0*vec2(0,-texelSize.y))/2*0.125;
		gl_FragData[0] += texture2D(colortex5,quarterResTC+2.0*vec2(-texelSize.x,0.0))/2*0.125;

		//0.125
		gl_FragData[0] += texture2D(colortex5,quarterResTC-2.0*vec2(texelSize.x,texelSize.y))/4.*0.125;
		gl_FragData[0] += texture2D(colortex5,quarterResTC+2.0*vec2(texelSize.x,texelSize.y))/4.*0.125;
		gl_FragData[0] += texture2D(colortex5,quarterResTC+vec2(-2.0*texelSize.x,2.0*texelSize.y))/4.*0.125;
		gl_FragData[0] += texture2D(colortex5,quarterResTC+vec2(2.0*texelSize.x,-2.0*texelSize.y))/4.*0.125;

		//0.125
		gl_FragData[0] += texture2D(colortex5,quarterResTC)*0.125;

        #if defined RF_FLOW_STREAKS && defined AURAM_RF_BACKEND
            if (all(lessThanEqual(quarterResTC, vec2(1.0)))) {
                gl_FragData[0].rgb += 8.0 * rfStreakLight(quarterResTC);
            }
        #endif

		gl_FragData[0].rgb = clamp(gl_FragData[0].rgb,0.0,65000.);
		if (quarterResTC.x > 1.0 - 3.5*texelSize.x || quarterResTC.y > 1.0 -3.5*texelSize.y || quarterResTC.x < 3.5*texelSize.x || quarterResTC.y < 3.5*texelSize.y) gl_FragData[0].rgb = vec3(0.0);

}
