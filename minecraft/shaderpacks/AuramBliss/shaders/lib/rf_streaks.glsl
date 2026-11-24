#ifndef AURAM_RF_STREAKS_GLSL
#define AURAM_RF_STREAKS_GLSL

uniform usampler3D auramRfVolume;
uniform bool auramRfActive;
uniform vec3 auramRfOrigin;
uniform vec3 auramRfMinimum;
uniform vec3 auramRfMaximum;
uniform vec3 auramRfPhaseOrigin;

const float rfTau = 6.2831853;
const float rfPulseSpacing = 2.0;

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
    vec2 wander = rfWander(along, seed);
    float angle = rfTau * (along * 0.25 + seed) + 0.65 * wander.x;
    float radius = 0.201 + 0.027 * sin(rfTau * (along * 0.125 + seed));
    vec2 offset = radius * vec2(cos(angle), sin(angle)) + 0.095 * wander;
    point[(axis + 1) % 3] += offset.x;
    point[(axis + 2) % 3] += offset.y;
    return point;
}

vec3 rfCurve(vec3 a, vec3 b, vec3 c, vec3 d, float t) {
    vec3 ab = mix(a, b, t), bc = mix(b, c, t), cd = mix(c, d, t);
    return mix(mix(ab, bc, t), mix(bc, cd, t), t);
}

float rfTravelPhase() {
    const float originalSpeed = 3.45;
    const float noiseInterval = 0.25;
    float clock = frameTimeCounter / noiseInterval;
    float progress = fract(clock);
    float noise = rfWanderLattice(vec2(floor(clock), 5.0), 0.347).x;
    // This displacement and its slope meet at zero across noise intervals.
    // The derivative of the normalized bump ranges from -1 to +1.
    float bump = progress * (1.0 - progress);
    float jitter = 2.0 * noise * noiseInterval * 5.1961524 * bump * bump;
    return mod(originalSpeed * (5.0 * frameTimeCounter + jitter), rfPulseSpacing);
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

vec3 rfPulse(vec3 ro, vec3 rd, vec2 interval, ivec3 block, float phase,
             int inFace, uint inRate, int outFace, uint outRate) {
    const float trail = 0.48;
    // Each hop flips grid parity. Both sides of a shared face have the same
    // phase modulo two, even when the next hop turns or reverses world axes.
    float centerPhase = mod(dot(vec3(block) + auramRfPhaseOrigin, vec3(1.0)), rfPulseSpacing);
    float start = centerPhase - (inFace < 0 ? 0.0 : 0.5);
    float stop = centerPhase + (outFace < 0 ? 0.0 : 0.5);
    float head = floor((stop - phase + trail) / rfPulseSpacing) * rfPulseSpacing + phase;
    float first = max(start, head - trail);
    float last = min(stop, head);
    if (last <= first) return vec3(0.0);

    float energyIn = rfBrightness(inRate);
    float energyOut = rfBrightness(outRate);
    if (max(energyIn, energyOut) <= 0.0) return vec3(0.0);

    vec3 center = vec3(block) + 0.5;
    vec3 a = inFace < 0 ? center : rfFacePoint(center, inFace, true);
    vec3 d = outFace < 0 ? center : rfFacePoint(center, outFace, false);
    vec3 enter = inFace < 0 ? rfFaceNormal(outFace) : -rfFaceNormal(inFace);
    vec3 exit = outFace < 0 ? -rfFaceNormal(inFace) : rfFaceNormal(outFace);
    // Face-normal tangents and shared endpoints make the curve smooth across blocks.
    float reach = (inFace < 0 || outFace < 0) ? 0.17 : 0.29;
    vec3 b = a + reach * enter;
    vec3 c = d - reach * exit;

    float beginT = (first - start) / (stop - start);
    float endT = (last - start) / (stop - start);
    vec3 points[4];
    for (int i = 0; i < 4; i++) {
        points[i] = rfCurve(a, b, c, d, mix(beginT, endT, float(i) / 3.0));
    }
    float d2 = 0.0256;
    float closestT = 0.0;
    for (int i = 0; i < 3; i++) {
        float alongSegment;
        float distance = rfDistanceSquared(ro, rd, interval, points[i], points[i + 1], alongSegment);
        if (distance < d2) {
            d2 = distance;
            closestT = mix(beginT, endT, (float(i) + alongSegment) / 3.0);
        }
    }
    if (d2 >= 0.0256) return vec3(0.0);

    // Keep the subpixel filament stable while its wider bloom carries most of the visible glow.
    const float filament = 0.0055;
    float pixelRadius = interval.x / max(viewHeight, 1.0);
    float widthSquared = filament * filament + pixelRadius * pixelRadius;
    float core = exp2(-0.72135 * d2 / widthSquared) * filament * inversesqrt(widthSquared);
    float halo = (0.19 * exp2(-275.0 * d2) + 0.12 * exp2(-85.0 * d2))
               * (1.0 - smoothstep(0.010, 0.0256, d2));
    float age = (head - mix(start, stop, closestT)) / trail;
    float tail = smoothstep(0.0, 0.16, age) * (1.0 - smoothstep(0.25, 1.0, age));
    vec3 world = auramRfPhaseOrigin + rfCurve(a, b, c, d, closestT);
    float hue = 5.0 * frameTimeCounter - age * 1.8 + rfTau * dot(world, vec3(3.0, 1.0, 5.0)) / 32.0;
    vec3 shimmer = 0.55 + 0.45 * cos(hue + vec3(0.0, 2.0944, 4.1888));
    vec3 tint = mix(vec3(1.0), shimmer, 0.42);
    float energy = mix(energyIn, energyOut, closestT);
    return tint * energy * tail * (core + halo);
}

int rfFeedingFace(uint rates[6], int otherFace) {
    int bestFace = -1;
    uint bestRate = 0u;
    for (int face = 0; face < 6; face++) {
        uint rate = rates[face];
        if (face == otherFace || rate == 0u) continue;
        if (rate > bestRate || (rate == bestRate && face == (otherFace ^ 1))) {
            bestRate = rate;
            bestFace = face;
        }
    }
    return bestFace;
}

vec3 rfCell(vec3 ro, vec3 rd, vec2 interval, ivec3 cell, uvec3 data, float phase) {
    uint incoming[6], outgoing[6];
    for (int axis = 0; axis < 3; axis++) {
        uint traffic = data[axis];
        incoming[axis * 2] = (traffic >> 8u) & 0xffu;
        outgoing[axis * 2] = traffic & 0xffu;
        incoming[axis * 2 + 1] = (traffic >> 16u) & 0xffu;
        outgoing[axis * 2 + 1] = traffic >> 24u;
    }

    // The field stores traffic per face. At forks, start each outgoing strand
    // from the strongest compatible input and also draw any remaining inputs.
    vec3 light = vec3(0.0);
    int usedInputs = 0;
    for (int outFace = 0; outFace < 6; outFace++) {
        if (outgoing[outFace] == 0u) continue;
        int inFace = rfFeedingFace(incoming, outFace);
        uint inRate = 0u;
        if (inFace >= 0) {
            usedInputs |= 1 << inFace;
            inRate = incoming[inFace];
        }
        light = max(light, rfPulse(ro, rd, interval, cell, phase, inFace, inRate, outFace, outgoing[outFace]));
    }
    for (int inFace = 0; inFace < 6; inFace++) {
        if (incoming[inFace] == 0u || (usedInputs & (1 << inFace)) != 0) continue;
        int outFace = rfFeedingFace(outgoing, inFace);
        uint outRate = outFace < 0 ? 0u : outgoing[outFace];
        light = max(light, rfPulse(ro, rd, interval, cell, phase, inFace, incoming[inFace], outFace, outRate));
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
    float phase = rfTravelPhase();
    for (int i = 0; i < 96; i++) {
        if (first >= last || any(lessThan(cell, low)) || any(greaterThanEqual(cell, high))) break;
        int axis = cellExit.x <= cellExit.y && cellExit.x <= cellExit.z ? 0 : (cellExit.y <= cellExit.z ? 1 : 2);
        float stop = min(cellExit[axis], last);
        uvec3 rates = texelFetch(auramRfVolume, cell, 0).rgb;
        if (stop > first && any(notEqual(rates, uvec3(0u)))) {
            light += rfCell(ro, rd, vec2(first, stop), cell, rates, phase);
        }
        first = stop;
        cell[axis] += stepDir[axis];
        cellExit[axis] += advance[axis];
    }
    return light * RF_STREAK_INTENSITY;
}
#endif
