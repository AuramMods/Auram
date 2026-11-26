#ifndef AURAM_RF_PULSE_GLSL
#define AURAM_RF_PULSE_GLSL

float rfHash(vec3 p) {
    return fract(sin(dot(mod(p, 256.0), vec3(17.93, 41.71, 113.69))) * 43758.5453);
}

vec3 rfAxis(int axis) {
    if (axis == 0) return vec3(1.0, 0.0, 0.0);
    if (axis == 1) return vec3(0.0, 1.0, 0.0);
    return vec3(0.0, 0.0, 1.0);
}

vec2 rfWander(float worldAxial, float seed) {
    // Sample in world space so neighboring conduit halves share the same drifting path.
    vec2 p = vec2(worldAxial * 2.0, frameTimeCounter * (2.24 / 3.0)) + seed * vec2(15.0, 11.0);
    vec2 grid = floor(p);
    vec2 blend = smoothstep(vec2(0.0), vec2(1.0), fract(p));
    vec2 early = mix(rfWanderLattice(grid, seed), rfWanderLattice(grid + vec2(1.0, 0.0), seed), blend.x);
    vec2 late = mix(rfWanderLattice(grid + vec2(0.0, 1.0), seed),
                    rfWanderLattice(grid + vec2(1.0, 1.0), seed), blend.x);
    return mix(early, late, blend.y);
}

vec3 rfFaceNormal(int face) {
    return rfAxis(face / 2) * ((face & 1) == 0 ? 1.0 : -1.0);
}

vec3 rfFacePoint(vec3 center, int face, bool entering) {
    int axis = face / 2;
    vec3 point = center + 0.5 * rfFaceNormal(face);
    vec3 world = point + auramRfPhaseOrigin;
    float along = world[axis];
    world[axis] = 0.0;
    bool negative = ((face & 1) != 0) != entering;
    float seed = rfHash(world + vec3(float(axis) * 19.0 + (negative ? 7.0 : 0.0)));
    vec2 wander = rfWander(along * 1.5, seed);
    float angle = rfTau * (along * 0.0625 + seed) + 2.75 * wander.x;
    float radius = 0.221 + 0.100 * wander.y;
    vec2 offset = radius * vec2(cos(angle), sin(angle));
    point[(axis + 1) % 3] += offset.x;
    point[(axis + 2) % 3] += offset.y;
    return point;
}

vec3 rfCurve(vec3 a, vec3 b, vec3 c, vec3 d, vec3 center, vec3 bend, vec3 twist, float t) {
    vec3 ab = mix(a, b, t), bc = mix(b, c, t), cd = mix(c, d, t);
    vec3 point = mix(mix(ab, bc, t), mix(bc, cd, t), t);
    // The perturbation and its derivative vanish at both faces.
    float middle = t * (1.0 - t);
    vec3 drift = 16.0 * middle * middle * (bend + twist * (3.5 * t - 1.75));
    vec3 room = max(vec3(0.0), 0.322 - abs(point - center));
    return point + clamp(drift, -room, room);
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

struct RfPulseParameters {
    vec4 a;
    vec4 b;
    vec4 c;
    vec4 d;
    vec4 bend;
    vec4 twist;
    float shimmer;
};

bool rfPreparePulse(ivec3 block, float phase, int inFace, uint inRate, int outFace, uint outRate,
                    out RfPulseParameters pulse) {
    float centerPhase = rfCenterPhase(block);
    vec3 center = vec3(block) + 0.5;
    vec3 worldCell = center + auramRfPhaseOrigin;
    vec3 inputFace = inFace < 0 ? vec3(0.0) : 0.5 * rfFaceNormal(inFace);
    vec3 outputFace = outFace < 0 ? vec3(0.0) : 0.5 * rfFaceNormal(outFace);
    float start = centerPhase - (inFace < 0 ? 0.0 : 0.5) + rfPhaseShift(worldCell + inputFace);
    float stop = centerPhase + (outFace < 0 ? 0.0 : 0.5) + rfPhaseShift(worldCell + outputFace);
    float head = floor((stop - phase + rfTrailLength) / rfPulseSpacing) * rfPulseSpacing + phase;
    if (head <= start) return false;

    float energyIn = rfBrightness(inRate);
    float energyOut = rfBrightness(outRate);
    if (max(energyIn, energyOut) <= 0.0) return false;

    vec3 a = inFace < 0 ? center : rfFacePoint(center, inFace, true);
    vec3 d = outFace < 0 ? center : rfFacePoint(center, outFace, false);
    vec3 enter = inFace < 0 ? rfFaceNormal(outFace) : -rfFaceNormal(inFace);
    vec3 exit = outFace < 0 ? -rfFaceNormal(inFace) : rfFaceNormal(outFace);
    // Face-normal tangents and shared endpoints make the curve smooth across blocks.
    float reach = (inFace < 0 || outFace < 0) ? 0.17 : 0.29;
    vec3 b = a + reach * enter;
    vec3 c = d - reach * exit;
    float seed = rfHash(worldCell + vec3(float(1 + inFace), float(1 + outFace), 13.0));
    vec2 crosswind = rfWander(dot(worldCell, vec3(1.0, 3.0, 5.0)) * 1.5, seed);
    float lift = rfWander(dot(worldCell, vec3(5.0, 2.0, 3.0)), seed + 0.39).x;
    vec3 course = normalize(enter + exit);
    vec3 eddy = vec3(crosswind.x, lift, crosswind.y);
    vec3 bend = 0.23 * (eddy - course * dot(course, eddy));
    vec3 twist = cross(course, bend);

    pulse.a = vec4(a, start);
    pulse.b = vec4(b, stop);
    pulse.c = vec4(c, head);
    pulse.d = vec4(d, energyIn);
    pulse.bend = vec4(bend, energyOut);
    pulse.twist = vec4(twist, float(inRate | (outRate << 8u)));
    pulse.shimmer = (5.0 / 3.0) * frameTimeCounter + rfTau * dot(worldCell, vec3(3.0, 1.0, 5.0)) / 32.0;
    return true;
}

vec3 rfTracePulse(vec3 ro, vec3 rd, vec2 interval, ivec3 block, RfPulseParameters pulse, bool prepared) {
    vec3 center = vec3(block) + 0.5;
    vec3 a = pulse.a.xyz, b = pulse.b.xyz, c = pulse.c.xyz, d = pulse.d.xyz;
    vec3 bend = pulse.bend.xyz, twist = pulse.twist.xyz;
    float start = pulse.a.w, stop = pulse.b.w, head = pulse.c.w;
    float energyIn = pulse.d.w, energyOut = pulse.bend.w;
    uint pairRates = uint(pulse.twist.w);
    uint inRate = pairRates & 255u, outRate = pairRates >> 8u;
    vec3 light = vec3(0.0);
    // A voxel can contain both the fading end of one band and the tip of the next.
    for (int band = 0; band < 2 && head > start; band++, head -= rfPulseSpacing) {
        float first = max(start, head - rfTrailLength);
        float last = min(stop, head);
        if (last <= first) continue;

        float beginT = (first - start) / (stop - start);
        float endT = (last - start) / (stop - start);
        vec3 pointA = rfCurve(a, b, c, d, center, bend, twist, beginT);
        float d2 = 0.0306;
        float closestT = 0.0;
        const float segmentStep = 1.0 / float(RF_CURVE_SEGMENTS);
        for (int i = 1; i <= RF_CURVE_SEGMENTS; i++) {
            vec3 pointB = rfCurve(a, b, c, d, center, bend, twist, mix(beginT, endT, float(i) * segmentStep));
            float alongSegment;
            float distance = rfDistanceSquared(ro, rd, interval, pointA, pointB, alongSegment);
            if (distance < d2) {
                d2 = distance;
                closestT = mix(beginT, endT, (float(i - 1) + alongSegment) * segmentStep);
            }
            pointA = pointB;
        }
        if (d2 >= 0.0306) continue;

        const float filament = 0.0035;
        float pixelRadius = interval.x / max(viewHeight, 1.0);
        float widthSquared = filament * filament + pixelRadius * pixelRadius;
        float core = exp2(-0.72135 * d2 / widthSquared) * filament * inversesqrt(widthSquared);
        float glow = (0.44 * exp2(-300.0 * d2) + 0.29 * exp2(-65.0 * d2))
                   * (1.0 - smoothstep(0.009, 0.0306, d2));
        float age = (head - mix(start, stop, closestT)) / rfTrailLength;
        vec3 point = rfCurve(a, b, c, d, center, bend, twist, closestT);
        float energy = mix(energyIn, energyOut, closestT);
        float tintRate = inRate == 0u || outRate == 0u ? float(max(inRate, outRate))
                                                      : mix(float(inRate), float(outRate), closestT);
        vec3 tint;
        if (prepared) {
            float angle = pulse.shimmer - age * 3.1 + rfTau * dot(point - center, vec3(3.0, 1.0, 5.0)) / 32.0;
            tint = rfBandTintFromWave(0.5 + 0.5 * cos(angle), tintRate);
        } else {
            tint = rfBandTint(auramRfPhaseOrigin + point, age, tintRate);
        }
        light = max(light, tint * energy * rfEnvelope(age) * (2.4 * core + glow * RF_HALO_BRIGHTNESS));
    }
    return light;
}

vec3 rfPulse(vec3 ro, vec3 rd, vec2 interval, ivec3 block, float phase,
             int inFace, uint inRate, int outFace, uint outRate) {
    RfPulseParameters pulse;
    if (!rfPreparePulse(block, phase, inFace, inRate, outFace, outRate, pulse)) return vec3(0.0);
    return rfTracePulse(ro, rd, interval, block, pulse, false);
}

#endif
