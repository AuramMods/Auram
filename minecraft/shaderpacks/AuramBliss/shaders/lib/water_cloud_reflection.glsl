vec3 auramWaterSkyDirection(vec3 reflected, vec3 surfaceOffset, float cameraAltitude) {
    #if defined VOLUMETRIC_CLOUDS && defined CloudLayer0
        #ifdef AETHER_SHADER
            float cloudHeight = AETHER_CLOUD_BASE + AETHER_CLOUD_THICKNESS * 0.5;
        #else
            float cloudHeight = CloudLayer0_height + 50.0;
        #endif
        float heightAboveWater = cloudHeight - (cameraAltitude + surfaceOffset.y);
        if (heightAboveWater > 0.0 && reflected.y > 0.002) {
            // The sky atlas was rendered from the camera. Aim its lookup at the
            // cloud point hit by the ray that starts at the water surface.
            vec3 cameraToCloud = surfaceOffset + reflected * (heightAboveWater / reflected.y);
            return normalize(mix(reflected, normalize(cameraToCloud), smoothstep(0.002, 0.018, reflected.y)));
        }
    #endif
    return reflected;
}
