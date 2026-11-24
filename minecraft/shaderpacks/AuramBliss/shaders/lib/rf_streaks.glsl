#ifndef AURAM_RF_STREAKS_GLSL
#define AURAM_RF_STREAKS_GLSL

uniform usampler3D auramRfVolume;
uniform bool auramRfActive;
uniform vec3 auramRfOrigin;
uniform vec3 auramRfMinimum;
uniform vec3 auramRfMaximum;
uniform vec3 auramRfPhaseOrigin;

const float rfTau = 6.2831853;

// Texels contain logarithmic rates, with 31 codes per decade. This response is
// intentionally editable in the pack so shader reloads can tune the visible range.
float rfBrightness(uint rate) {
    float decades = float(rate) / 31.0;
    return 0.14 * smoothstep(1.5, 4.0, decades) + 0.86 * smoothstep(4.0, 8.0, decades);
}

float rfHash(vec3 p) {
    return fract(sin(dot(mod(p, 256.0), vec3(17.93, 41.71, 113.69))) * 43758.5453);
}

vec3 rfAxis(int axis) {
    if (axis == 0) return vec3(1.0, 0.0, 0.0);
    if (axis == 1) return vec3(0.0, 1.0, 0.0);
    return vec3(0.0, 0.0, 1.0);
}

vec2 rfWanderLattice(vec2 grid, float seed) {
    grid = mod(grid, vec2(128.0, 16.0));
    vec3 hash = fract(grid.xyx * vec3(0.1031, 0.1030, 0.0973) + seed);
    hash += dot(hash, hash.yzx + 33.33);
    return 2.0 * fract((hash.xx + hash.yz) * hash.zy) - 1.0;
}

vec2 rfWander(float worldAxial, float seed) {
    // Sample in world space so neighboring conduit halves share the same drifting path.
    vec2 p = vec2(worldAxial * 0.5, frameTimeCounter * 0.16) + seed * vec2(15.0, 11.0);
    vec2 grid = floor(p);
    vec2 blend = smoothstep(vec2(0.0), vec2(1.0), fract(p));
    vec2 early = mix(rfWanderLattice(grid, seed), rfWanderLattice(grid + vec2(1.0, 0.0), seed), blend.x);
    vec2 late = mix(rfWanderLattice(grid + vec2(0.0, 1.0), seed),
                    rfWanderLattice(grid + vec2(1.0, 1.0), seed), blend.x);
    return mix(early, late, blend.y);
}

vec3 rfCoil(vec3 center, vec3 axis, vec3 radialA, vec3 radialB, float axial, float worldAxial, float seed) {
    vec2 wander = rfWander(worldAxial, seed);
    float angle = rfTau * (worldAxial * 0.25 + seed) + 0.65 * wander.x;
    float radius = 0.201 + 0.027 * sin(rfTau * (worldAxial * 0.125 + seed));
    vec2 offset = radius * vec2(cos(angle), sin(angle)) + 0.095 * wander;
    return center + axis * axial + radialA * offset.x + radialB * offset.y;
}

// Closest approach between the pixel's ray interval and one short strand segment.
float rfDistanceSquared(vec3 ro, vec3 rd, vec2 interval, vec3 a, vec3 b, out float alongSegment) {
    vec3 segment = b - a;
    vec3 relative = a - ro;
    float lengthSquared = max(dot(segment, segment), 1e-7);
    float parallel = dot(rd, segment);
    float alongRay = dot(rd, relative);
    float s = clamp((alongRay * parallel - dot(segment, relative))
        / max(lengthSquared - parallel * parallel, 1e-7), 0.0, 1.0);
    float t = clamp(alongRay + s * parallel, interval.x, interval.y);
    s = clamp(dot(ro + rd * t - a, segment) / lengthSquared, 0.0, 1.0);
    t = clamp(alongRay + s * parallel, interval.x, interval.y);
    alongSegment = s;
    vec3 miss = relative + segment * s - rd * t;
    return dot(miss, miss);
}

vec3 rfPulse(vec3 ro, vec3 rd, vec2 interval, ivec3 block, int axisIndex,
             vec2 halfEdge, float direction, uint rate) {
    float energy = rfBrightness(rate);
    if (energy <= 0.0) return vec3(0.0);

    vec3 axis = rfAxis(axisIndex);
    vec3 radialA = rfAxis((axisIndex + 1) % 3);
    vec3 radialB = cross(axis, radialA);
    vec3 center = vec3(block) + 0.5;
    vec3 phaseCenter = center + auramRfPhaseOrigin;
    float axisCenter = dot(phaseCenter, axis);
    float seed = rfHash(phaseCenter - axisCenter * axis + vec3(float(axisIndex) * 19.0));
    vec2 along = direction * (axisCenter + halfEdge);
    float start = min(along.x, along.y), stop = max(along.x, along.y);

    // Short, tapered pulses leave quiet gaps between threads of light.
    const float spacing = 3.2;
    float trail = mix(0.39, 0.72, energy);
    float speed = 2.6 + 1.7 * seed;
    float phase = spacing * fract(frameTimeCounter * speed / spacing + seed + step(direction, 0.0) * 0.38);
    float head = floor((stop - phase + trail) / spacing) * spacing + phase;
    float first = max(start, head - trail);
    float last = min(stop, head);
    if (last <= first) return vec3(0.0);

    vec3 pointA = rfCoil(center, axis, radialA, radialB, direction * first - axisCenter, direction * first, seed);
    float middle = (first + last) * 0.5;
    vec3 pointB = rfCoil(center, axis, radialA, radialB, direction * middle - axisCenter, direction * middle, seed);
    vec3 pointC = rfCoil(center, axis, radialA, radialB, direction * last - axisCenter, direction * last, seed);
    float alongAB, alongBC;
    float distanceAB = rfDistanceSquared(ro, rd, interval, pointA, pointB, alongAB);
    float distanceBC = rfDistanceSquared(ro, rd, interval, pointB, pointC, alongBC);
    float d2 = min(distanceAB, distanceBC);
    if (d2 >= 0.0256) return vec3(0.0);

    // Keep the subpixel filament stable while its wider bloom carries most of the visible glow.
    const float filament = 0.0055;
    float pixelRadius = interval.x / max(viewHeight, 1.0);
    float widthSquared = filament * filament + pixelRadius * pixelRadius;
    float core = exp2(-0.72135 * d2 / widthSquared) * filament * inversesqrt(widthSquared);
    float halo = (0.19 * exp2(-275.0 * d2) + 0.12 * exp2(-85.0 * d2))
               * (1.0 - smoothstep(0.010, 0.0256, d2));
    float closest = distanceAB <= distanceBC ? mix(first, middle, alongAB) : mix(middle, last, alongBC);
    float age = (head - closest) / trail;
    float tail = smoothstep(0.0, 0.16, age) * (1.0 - smoothstep(0.25, 1.0, age));
    vec3 shimmer = 0.55 + 0.45 * cos(5.0 * frameTimeCounter - direction * closest * (rfTau * 0.25)
                                  + seed * rfTau + vec3(0.0, 2.0944, 4.1888));
    vec3 tint = mix(vec3(1.0), shimmer, 0.42);
    return tint * energy * tail * (core + halo);
}

vec3 rfCell(vec3 ro, vec3 rd, vec2 interval, ivec3 cell, uvec3 data) {
    vec3 light = vec3(0.0);
    for (int axis = 0; axis < 3; axis++) {
        uint traffic = data[axis];
        if (traffic == 0u) continue;
        if ((traffic & 0xffu) != 0u)
            light += rfPulse(ro, rd, interval, cell, axis, vec2(0.0, 0.5), 1.0, traffic & 0xffu);
        if (((traffic >> 8u) & 0xffu) != 0u)
            light += rfPulse(ro, rd, interval, cell, axis, vec2(0.0, 0.5), -1.0, (traffic >> 8u) & 0xffu);
        if (((traffic >> 16u) & 0xffu) != 0u)
            light += rfPulse(ro, rd, interval, cell, axis, vec2(-0.5, 0.0), 1.0, (traffic >> 16u) & 0xffu);
        if ((traffic >> 24u) != 0u)
            light += rfPulse(ro, rd, interval, cell, axis, vec2(-0.5, 0.0), -1.0, traffic >> 24u);
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
    float rayStop = 48.0;
    if (sceneDepth < 1.0) rayStop = min(rayStop, length(rfUnproject(uv, sceneDepth)));
    #ifdef DISTANT_HORIZONS
        float lodDepth = texture2D(dhDepthTex, uv * RENDER_SCALE).r;
        if (lodDepth < 1.0) {
            float lodDistance = linearizeDepthFast(lodDepth, dhNearPlane, dhFarPlane);
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
    for (int i = 0; i < 96; i++) {
        if (first >= last || any(lessThan(cell, low)) || any(greaterThanEqual(cell, high))) break;
        int axis = cellExit.x <= cellExit.y && cellExit.x <= cellExit.z ? 0 : (cellExit.y <= cellExit.z ? 1 : 2);
        float stop = min(cellExit[axis], last);
        uvec3 rates = texelFetch(auramRfVolume, cell, 0).rgb;
        if (stop > first && any(notEqual(rates, uvec3(0u)))) {
            light += rfCell(ro, rd, vec2(first, stop), cell, rates);
        }
        first = stop;
        cell[axis] += stepDir[axis];
        cellExit[axis] += advance[axis];
    }
    return light * RF_STREAK_INTENSITY;
}
#endif
