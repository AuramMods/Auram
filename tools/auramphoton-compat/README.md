# AuramPhoton Compatibility

Build this Forge 1.20.1 client module against the exact renderer jars in the Auram instance. Pass the Prism Launcher libraries directory and the instance's Minecraft mods directory to Gradle.

```sh
gradle jar -PprismLibraries="/path/to/PrismLauncher/libraries" \
  -PauramMods="/path/to/Auram/minecraft/mods"
```

The output is under `build/libs`. For isolated validation, use it with Forge 47.4.10, Embeddium 0.3.31, Oculus 1.8.0, and optionally Distant Horizons 2.4.5-b. Use the `AuramPhoton` folder or `AuramPhoton.zip` as the shader pack.

Oculus and DH each manage their own depth attachments during framebuffer recreation. The companion keeps DH targets out of Oculus’s vanilla depth rebinding, so a resize cannot leave Minecraft’s stencil texture attached beside DH’s depth texture. The companion also updates the DH viewport for the first draw after a resize and releases the color and depth textures DH replaces. During pipeline teardown it releases Oculus’s DH generic shader and opaque-depth copy after Oculus removes its overrides.

DH generic objects support both direct and instanced draws through Oculus. On macOS the built-in DH shaders read their cube geometry from the original vertex and index data embedded in the vertex shader. This avoids a driver fault when fetching the position buffer. The direct draw submits the same triangle order with a non-indexed draw call. The instanced draw keeps DH’s per-box attribute data and original indices.

Oculus sometimes preprocesses the pack before Distant Horizons exposes its enabled state. The module reconciles the pack's shader environment with the live DH configuration immediately before Oculus builds a dimension pipeline. It reuses the existing pack root and shader options, including when the shader pack is loaded from a ZIP.

Install the module for AuramPhoton's macOS Colored Lights setting. It replays the Embeddium shadow terrain vertices as points into a 2D integer atlas indexed by voxel coordinates, uses the pack's emission and tint palette, and performs the same incremental propagation through a pair of layered light textures. Shader reloads and dimension changes recreate the volume with the selected world pipeline. The Windows path uses Photon's compute shaders by default. The shader's `FORCE_APPLE_COMPAT` option selects the raster passes for cross-driver comparisons.

## Experimental Metal colored-light transport

On Apple Silicon, append `-PmacosMetal` to the build command to bundle the native library using Xcode's Clang. Install that jar with the matching AuramPhoton shader pack, and add `-Dauramphoton.metal=true` to the client JVM arguments. The Metal path uses the same occupied voxels, emission and tint palette, volume settings, and previous-frame camera reprojection. It uses one shared rectangle texture for the current light volume.

The companion attempts to load Metal, attach the shared surfaces, and compile the reprojection stage before choosing that shader variant. If setup fails, the client logs the cause and initializes the OpenGL colored-light path for the same shader options. Omit the JVM argument to use the usual backend. The build command without `-PmacosMetal` works without Xcode and produces a portable jar.

The companion fixes the Distant Horizons 2.4.5-b polygon mode query. OpenGL writes both face modes. The companion supplies space for both values and preserves DH's existing restore behavior.
