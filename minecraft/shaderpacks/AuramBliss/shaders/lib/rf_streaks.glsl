#ifndef AURAM_RF_STREAKS_GLSL
#define AURAM_RF_STREAKS_GLSL

#include "/lib/rf_field.glsl"

#include "/lib/rf_pulse.glsl"

#include "/lib/rf_routes.glsl"

#ifdef AURAM_BLISS_RF_PREPARED
    #include "/lib/rf_prepared.glsl"
    uniform bool auramRfPrepared;
#endif

vec3 rfCell(vec3 ro, vec3 rd, vec2 interval, ivec3 cell, uvec4 data, float phase) {
    vec3 light = vec3(0.0);
    #ifdef AURAM_BLISS_RF_PREPARED
        if (auramRfPrepared) {
            uint indexWord = rfPreparedCells[rfPreparedCellIndex(cell)];
            if (indexWord != rfPreparedOverflow) {
                uint first = indexWord >> 4u;
                uint count = indexWord & 15u;
                for (uint i = 0u; i < count; i++) {
                    light = max(light, rfTracePulse(ro, rd, interval, cell, rfPreparedPulses[first + i], true));
                }
                return light;
            }
        }
    #endif
    ivec4 routes[12];
    int count = rfFindRoutes(data.rgb, routes);
    float circuitPhase = rfCircuitPhase(data.a, phase);
    for (int i = 0; i < count; i++) {
        ivec4 route = routes[i];
        light = max(light, rfPulse(ro, rd, interval, cell, circuitPhase, route.x, uint(route.y), route.z, uint(route.w)));
    }
    return light;
}

vec3 rfUnproject(vec2 uv, float depth) {
    vec4 scene = gbufferProjectionInverse * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return scene.xyz / scene.w;
}

vec3 rfStreakLight(vec2 uv) {
    if (!auramRfActive) return vec3(0.0);
    float sceneDepth = texture2D(depthtex0, uv * RENDER_SCALE).r;
    vec3 viewRay = rfUnproject(uv, 0.9);
    float rayStop = float(RF_RENDER_RANGE);
    if (sceneDepth < 1.0) rayStop = min(rayStop, length(rfUnproject(uv, sceneDepth)));
    #ifdef DISTANT_HORIZONS
        float lodDepth = texture2D(dhDepthTex, uv * RENDER_SCALE).r;
        if (lodDepth < 1.0) {
            float lodDistance = (dhNearPlane * dhFarPlane) / (lodDepth * (dhNearPlane - dhFarPlane) + dhFarPlane);
            rayStop = min(rayStop, lodDistance * length(viewRay) / max(abs(viewRay.z), 1e-4));
        }
    #endif

    vec3 ro = gbufferModelViewInverse[3].xyz - auramRfOrigin;
    vec3 rd = normalize(mat3(gbufferModelViewInverse) * viewRay);
    vec3 signRd = mix(vec3(-1.0), vec3(1.0), step(vec3(0.0), rd));
    vec3 rdInverse = signRd / max(abs(rd), vec3(1e-6));
    vec3 a = (auramRfMinimum - ro) * rdInverse;
    vec3 b = (auramRfMaximum - ro) * rdInverse;
    vec3 enter = min(a, b), leave = max(a, b);
    float first = max(max(enter.x, enter.y), max(enter.z, 0.0));
    float last = min(min(leave.x, leave.y), min(leave.z, rayStop));
    if (last <= first) return vec3(0.0);

    vec3 point = ro + rd * (first + 0.0001);
    ivec3 cell = ivec3(floor(point));
    ivec3 stepDir = ivec3(signRd);
    vec3 cellExit = (vec3(cell) + max(signRd, vec3(0.0)) - ro) * rdInverse;
    vec3 advance = abs(rdInverse);
    ivec3 low = ivec3(auramRfMinimum);
    ivec3 high = ivec3(auramRfMaximum);

    vec3 light = vec3(0.0);
    float phase = rfTravelPhase();
    for (int i = 0; i < RF_TRACE_CELLS; i++) {
        if (first >= last || any(lessThan(cell, low)) || any(greaterThanEqual(cell, high))) break;
        int axis = cellExit.x <= cellExit.y && cellExit.x <= cellExit.z ? 0 : (cellExit.y <= cellExit.z ? 1 : 2);
        float stop = min(cellExit[axis], last);
        uvec4 data = texelFetch(auramRfVolume, cell, 0);
        if (stop > first && any(notEqual(data.rgb, uvec3(0u)))) {
            light += rfCell(ro, rd, vec2(first, stop), cell, data, phase);
        }
        first = stop;
        cell[axis] += stepDir[axis];
        cellExit[axis] += advance[axis];
    }
    return light * RF_STREAK_INTENSITY;
}
#endif
