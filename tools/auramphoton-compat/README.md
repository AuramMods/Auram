# AuramPhoton Compatibility

Build this Forge 1.20.1 client module against the exact renderer jars in the Auram instance. Pass the Prism Launcher libraries directory and the instance's Minecraft mods directory to Gradle.

```sh
gradle jar -PprismLibraries="/path/to/PrismLauncher/libraries" \
  -PauramMods="/path/to/Auram/minecraft/mods"
```

The output is under `build/libs`. For isolated validation, use it with Forge 47.4.10, Embeddium 0.3.31, Oculus 1.8.0, and optionally Distant Horizons 2.4.5-b. Use the `AuramPhoton` folder or `AuramPhoton.zip` as the shader pack.

Oculus sometimes preprocesses the pack before Distant Horizons exposes its enabled state. The module reconciles the pack's shader environment with the live DH configuration immediately before Oculus builds a dimension pipeline. It reuses the existing pack root and shader options, including when the shader pack is loaded from a ZIP.


Install the module for AuramPhoton's macOS Colored Lights setting. It replays the Embeddium shadow terrain vertices as points into a 2D integer atlas indexed by voxel coordinates, uses the pack's emission and tint palette, and performs the same incremental propagation through a pair of layered light textures. Shader reloads and dimension changes recreate the volume with the selected world pipeline. The Windows path uses Photon's compute shaders by default. The shader's `FORCE_APPLE_COMPAT` option selects the raster passes for cross-driver comparisons.
