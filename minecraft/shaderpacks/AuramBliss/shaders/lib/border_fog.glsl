float borderFogFactor(vec3 playerPosition, float renderDistance) {
    // Preserve the original radial fade for Distant Horizons.
    #ifdef DISTANT_HORIZONS
        float distanceToCamera = length(playerPosition);
    #else
        float distanceToCamera = length(playerPosition.xz);
    #endif

    float fog = smoothstep(renderDistance * (2.0 / 3.0), renderDistance, distanceToCamera);
    float elevation = max(playerPosition.y, 0.0) / max(length(playerPosition), 0.001);
    return fog * exp(-160.0 * elevation * elevation);
}
