#ifndef AURAM_VAPOR_BOUNDS
#define AURAM_VAPOR_BOUNDS

#ifdef AURAM_VAPOR_BACKEND
uniform bool auramVaporActive;
    #ifdef AURAM_VAPOR_RADIUS
uniform vec3 auramVaporCenter;
    #endif
#endif

vec3 vaporCenter() {
    #if defined AURAM_VAPOR_BACKEND && defined AURAM_VAPOR_RADIUS
        if (auramVaporActive) return auramVaporCenter;
    #endif
    return vec3(0.0);
}

// Positions are camera-relative. The interval is in blocks along the view ray.
vec2 vaporBoundsInterval(vec3 origin, vec3 direction, vec3 boundsMin, vec3 boundsMax, float rayEnd) {
    bvec3 parallel = lessThan(abs(direction), vec3(1e-6));
    bvec3 inside = bvec3(
        origin.x >= boundsMin.x && origin.x < boundsMax.x,
        origin.y >= boundsMin.y && origin.y < boundsMax.y,
        origin.z >= boundsMin.z && origin.z < boundsMax.z);
    if ((parallel.x && !inside.x) || (parallel.y && !inside.y) || (parallel.z && !inside.z))
        return vec2(0.0);

    vec3 signDirection = mix(vec3(-1.0), vec3(1.0), step(vec3(0.0), direction));
    vec3 inverseDirection = signDirection / max(abs(direction), vec3(1e-6));
    vec3 a = (boundsMin - origin) * inverseDirection;
    vec3 b = (boundsMax - origin) * inverseDirection;
    vec3 nearPlane = min(a, b);
    vec3 farPlane = max(a, b);
    vec3 entry = vec3(parallel.x ? 0.0 : nearPlane.x, parallel.y ? 0.0 : nearPlane.y, parallel.z ? 0.0 : nearPlane.z);
    vec3 exit = vec3(parallel.x ? rayEnd : farPlane.x, parallel.y ? rayEnd : farPlane.y, parallel.z ? rayEnd : farPlane.z);
    float first = max(max(entry.x, entry.y), max(entry.z, 0.0));
    float last = min(min(exit.x, exit.y), min(exit.z, rayEnd));
    return last > first ? vec2(first, last) : vec2(0.0);
}

vec2 vaporCubeInterval(vec3 origin, vec3 direction, vec3 center, float surfaceDistance) {
    const vec3 radius = vec3(float(VAPOR_RENDER_RANGE));
    float rayEnd = surfaceDistance >= 0.0 ? surfaceDistance : length(abs(origin - center) + radius);
    return vaporBoundsInterval(origin, direction, center - radius, center + radius, rayEnd);
}

float vaporEdgeFade(vec3 fromCenter) {
    vec3 offset = abs(fromCenter);
    float edge = max(offset.x, max(offset.y, offset.z));
    return 1.0 - smoothstep(float(VAPOR_RENDER_RANGE) - 4.0, float(VAPOR_RENDER_RANGE), edge);
}

#endif
