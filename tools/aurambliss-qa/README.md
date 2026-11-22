# AuramBliss isolated renderer replay

Use Python 3, a JDK with `javac` and `jar` on PATH, a native Java 17 executable, and a PrismLauncher installation that has launched Auram on this machine. The runner copies an existing fixture save and gives it the local directory name `ShaderLab`. It mutes every launch, disables narrator and accessibility onboarding, and creates a hidden GLFW window with a real GPU context. Find the resulting launcher arguments, Oculus-transformed shaders and logs in the printed work directory.

```sh
python3 tools/aurambliss-qa/run.py "/path/to/PrismLauncher/instances/Auram" \
  "/path/to/a/disposable/cached-shore-save" \
  --shader minecraft/shaderpacks/AuramBliss \
  --java "/path/to/native/java" \
  --companion "/path/to/auramphoton-compat-0.1.0-dh333.jar" \
  --options tools/aurambliss-qa/fixtures/heavy.properties
```

For the minimal baseline use a vanilla-compatible local world and omit the options. Add `--no-dh` to run without the LOD renderer. For the complete pack add `--full --size 1280x720 --heap 20g --timeout 650` and a disposable representative save. Supply `--dh-config` with a copy configured to enable DH rendering for that test. Use the same save, frame size and options on Windows, then repeat with `--force-compat`.

A successful replay reports the selected shader, pipeline creation, world entry and, when enabled, DH renderer setup. Inspect `console.log` and `minecraft/patched_shaders` for the resulting program and diagnostics. This runner observes startup and an in-world interval. Pixel, LOD, lifecycle and performance measurements require their scenario-specific probes.

Build the optional Forge probe from the same checkout and run a texture readback.

```sh
python3 tools/aurambliss-qa/build_probe.py "/path/to/PrismLauncher/instances/Auram" /tmp/bliss-probe.jar
python3 tools/aurambliss-qa/run.py "/path/to/PrismLauncher/instances/Auram" \
  "/path/to/a/disposable/cached-shore-save" \
  --shader minecraft/shaderpacks/AuramBliss --java "/path/to/native/java" \
  --companion "/path/to/auramphoton-compat-0.1.0-dh333.jar" \
  --dh-config "/path/to/an/enabled/DistantHorizons.toml" \
  --probe /tmp/bliss-probe.jar --readback depth
```

`depth` compares the current packed target with the live source textures. Use `light` with LPV enabled to inspect the published volume, `entities` to spawn the test emitters in the disposable copy, or `perf` to collect 90 consecutive frame intervals and available GPU timings for LPV propagation. The probe writes machine-readable events to the console and the runner collects them in `readback.json`.

Use `--lifecycle tools/aurambliss-qa/fixtures/lifecycle.txt` with a cached shoreline, both additional packs supplied as `--additional-shader`, and a Bliss configuration with LPV enabled. The replay asserts the selected pack, framebuffer size, DH depth and LPV texture availability, and GL status while it resizes, reloads, and switches shaders. Use `--lifecycle tools/aurambliss-qa/fixtures/dimensions.txt` to exercise the vanilla dimensions and world re-entry. The test reads the final framebuffer into `minecraft/bliss-frames`.

Generate the control inventory with `python3 tools/aurambliss-qa/inventory.py minecraft/shaderpacks/AuramBliss /tmp/bliss-options.json`. The output lists profile values, menu paths, source lines, suggested scene checks, dimensions and texture declarations.
