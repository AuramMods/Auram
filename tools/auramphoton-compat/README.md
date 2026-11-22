# AuramPhoton Compatibility

Build this Forge 1.20.1 client module against the exact renderer jars in the Auram instance. Pass the Prism Launcher libraries directory and the instance's Minecraft mods directory to Gradle.

```sh
gradle jar -PprismLibraries="/path/to/PrismLauncher/libraries" \
  -PauramMods="/path/to/Auram/minecraft/mods"
```

The output is under `build/libs`. The default jar targets Forge 47.4.10, Embeddium 0.3.31, Oculus 1.8.0, and optionally Distant Horizons 2.4.5-b. Use the `AuramPhoton` folder or `AuramPhoton.zip` as the shader pack.

For the Forge 47.4.29 and Distant Horizons 3.3.3 migration, add
`-PdistantHorizons333Jar="/path/to/DistantHorizons-3.3.3-1.20.1-fabric-forge.jar"`
to build the `-dh333.jar` companion. Install the companion built for the DH
version in that instance. Both builds use the same shader pack, Oculus version,
and Embeddium version. DH upgrades can migrate the world's LOD database. Test the
migration on a copy of the instance and world.

Add `-PauramCorePriority` when building the DH 3.3.3 companion for Auram with
AuramCore 1.0.25. This loads AuramCore's existing player-chunk tracker before
its legacy DH target check and reuses its scheduling policy on the new DH
queue. Omit the flag for a standalone shader test without AuramCore.

The DH 3.3.3 build uses Forge's generated Minecraft 1.20.1 SRG client jar in the
Prism libraries directory. In singleplayer, the companion refreshes DH's beacon
data when Minecraft publishes a changed beam or removes its block. Smooth Chunk
Save can keep its normal disk-save interval, and glass tint updates can refresh
the beam even when DH skips rebuilding unchanged terrain.

The companion initializes Minecraft's two field-of-view interpolation values at the neutral multiplier. The first world draw can run before the first camera tick. Initializing the values gives vanilla terrain, Oculus and Distant Horizons the configured projection from that first draw.

Oculus and DH each manage their own depth attachments during framebuffer recreation. The companion keeps DH targets out of Oculus’s vanilla depth rebinding, so a resize cannot leave Minecraft’s stencil texture attached beside DH’s depth texture. On DH 2.4.5-b, the companion also updates the viewport for the first draw after a resize and releases the color and depth textures DH replaces. DH 3.3.3 handles viewport dimensions and replaced depth textures in its renderer. During pipeline teardown the companion releases Oculus’s DH generic shader and opaque-depth copy after Oculus removes its overrides.

DH generic objects support both direct and instanced draws through Oculus. On macOS the built-in DH shaders read their cube geometry from the original vertex and index data embedded in the vertex shader. This avoids a driver fault when fetching the position buffer. The direct draw submits the same triangle order with a non-indexed draw call. The instanced draw keeps DH’s per-box attribute data and original indices. The 3.3.3 build applies this fix at the new OpenGL renderer and restores the macOS generic rendering path.

Oculus sometimes preprocesses the pack before Distant Horizons exposes its enabled state. The module reconciles the pack's shader environment with the live DH configuration immediately before Oculus builds a dimension pipeline. It reuses the existing pack root and shader options, including when the shader pack is loaded from a ZIP.

With Thaumaturge installed, AuramPhoton reads its live Warp Mist strength and applies the mist to DH terrain, water, and the sky behind them. It honors the player's saved DH fog setting while Oculus handles the shader pipeline. Nearby vanilla terrain retains its usual shading.

With Astral Sorcery installed, the companion assigns Oculus's sun and moon phases while Astral draws its disks in the main sky. AuramPhoton reads Astral's eclipse progress for the solar atlas, procedural obscuration and corona, sunlight attenuation, and lunar color. The vanilla and procedural disk options remain available during the events.

Astral's constellation stars and lines draw into Oculus's colored sky buffer. Their textures, vertex colors, and opacity follow Astral's renderer, and Photon Stars and Custom Sky remain independent controls.

Install the module for AuramPhoton's macOS Colored Lights setting. It replays the Embeddium shadow terrain vertices as points into a 2D integer atlas indexed by voxel coordinates, uses the pack's emission and tint palette, and performs the same incremental propagation through a pair of layered light textures. Shader reloads and dimension changes recreate the volume with the selected world pipeline. The Windows path uses Photon's compute shaders by default. The shader's `FORCE_APPLE_COMPAT` option selects the raster passes for cross-driver comparisons.

## Experimental Metal colored-light transport

On Apple Silicon, append `-PmacosMetal` to the build command to bundle the native library using Xcode's Clang. Install that jar with the matching AuramPhoton shader pack, and add `-Dauramphoton.metal=true` to the client JVM arguments. The Metal path uses the same occupied voxels, emission and tint palette, volume settings, and previous-frame camera reprojection. It uses one shared rectangle texture for the current light volume.

The companion attempts to load Metal, attach the shared surfaces, and compile the reprojection stage before choosing that shader variant. If setup fails, the client logs the cause and initializes the OpenGL colored-light path for the same shader options. Omit the JVM argument to use the usual backend. The build command without `-PmacosMetal` works without Xcode and produces a portable jar.

Both builds fix their Distant Horizons polygon mode query. OpenGL writes both face modes. The companion supplies space for both values and preserves DH's existing restore behavior.
