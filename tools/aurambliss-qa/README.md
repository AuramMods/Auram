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

`fixtures/full-pack.txt` uses a copied shoreline save facing water. Supply a labPBR pack with `--resource-pack` to cover material loading, both tone curves, Thaumaturge fog and the Aether. The fullscreen fixture targets an attached 1920 × 1080 auxiliary display named `Auram QA Display` and checks that the GLFW window stays hidden and unfocused. The Astral fixture asks the mod’s calendar for the next matching solar and lunar events.

To reproduce the scrolling shoreline benchmark, use the same source save, `--readback perf --camera-motion 22,-8,32 --seconds 12` and `LPV_ENABLED=true`, `LPV_SIZE=7`, `LPV_VL_FOG_ILLUMINATION=true`. The three motion values are the X displacement, Z displacement, and yaw turn in degrees over 90 frames. Repeat with `--cold-dh` and a config with `chunkGeneratorMode = "FEATURES"` to run during new LOD generation. The option skips DH databases when copying the save.

## GPU pass profiling

Add `--profile-passes --max-fps 260 --size 1280x720 --seconds 25` to the replay command with the rebuilt probe JAR. Pass profiling enables the frame timing readback. Use the same cached save and camera for each run. For a rendering-only benchmark, use a DH fixture with `generatorPlan = "DISABLED"`. The runner also accepts the existing `--camera-motion` option for a moving replay.

`readback.json` contains frame intervals and a `render_pass_timing` event with 90 samples. The GPU timings use elapsed queries whose results are collected after completion. The report includes:

- `draw/<program>` for each fullscreen shader draw and `compute/<program>` for pack compute dispatches when the GPU runs them.
- `stage/shadows`, `stage/dh`, `stage/dh-deferred` and `stage/lpv-propagation` when those stages run. Nested work is counted under the more specific label. With profiling enabled, LPV propagation appears here.
- `world/other` for the rest of the timed world render, including ordinary terrain and entities. `world` totals all intervals per frame.

Sort the named passes by GPU median and compare median and p95 frame times across repeated baseline and candidate runs. The draw names match the program files in the pack's dimension directory. For example, AuramBliss's `draw/composite3` runs the volumetric fog and cloud code included by `world0/composite3.fsh`. Compare screenshots from the same pose, time, and weather to verify that an optimization preserves the effect. A feature toggle can estimate a pass's cost.

## Crystal-chest benchmark

Generate a fixed clear-weather world fixture with `python3 tools/aurambliss-qa/chest_fixture.py /tmp/bliss-chest-scenes`. Pass its `lifecycle.txt` to the runner with the QA probe, the compatibility JAR, and Iron Chests via `--mod`. The replay needs a disposable Overworld save with the platform area clear around X 29–66, Y 73–89, Z 23–52. Use `--case small --rounds 1` for a quick check. The full fixture alternates the two backends for empty, 64 loaded, 512 loaded, and mixed-model cases. It also changes an inventory, moves the camera, and reloads resources. The `render_pass_timing` event reports average entity and world vertex-buffer draws and the number of item models served by the experimental cache. Direct chunk and Voxy submissions are timed in their own GPU spans.

Use `--no-dh` and repeat `--mod /path/to/mod.jar` for Neo-Voxy, AuramCore and, on macOS, GLA and its Forge loader dependencies. Use `--config /path/to/voxy-config.json` to copy a fixed Voxy configuration. The probe reports render-thread CPU time with frame intervals. Named GPU stages include LPV occupancy replay and Neo-Voxy shadows when those features run.

For repeated same-client measurements, add `measure label` or `profile label` to a lifecycle file. The runner waits for the frame sample, and `profile` also waits for the per-pass GPU sample. Both write their results to `benchmarks.json`. Set `--java-option=-Dauram.bliss.qa.frames=180` to increase each sample from 90 to 180 frames. Use `light-volume label` to save the RGBA8 light texture, including its handheld palette, to `minecraft/lpv-label.rgba8`. Use `capture label` for the matching screenshot.

Append `dx,dz,yaw` to a measurement for a repeatable camera move. Reset the player's pose before each repeated path. In the disposable Overworld fixture, `rf-routes count` reports bent routes through AuramCore's RF API at 100 million RF/t, starting near `33 75 34`. Use `rf-routes 0` to stop reporting those routes.

After the routes have appeared, use `freeze-shader-time 37.25` to hold shader animation and the uploaded RF circuit phases across matched captures. Resume animation with `freeze-shader-time off` before timing or changing routes. Use `rf-volume label` to sample active RF cells and face rates, and `packed-depth label` to sample the packed depth format, LOD coverage, and depth checksum.

## Windows shader source audit

Prepare the NVIDIA, AMD and Intel matrix and generate transformed GLSL on any supported host with Oculus 1.8.0. The test builds actual shader-pack programs with a Windows OpenGL 4.6 environment, then passes the resulting stages to `glslangValidator` for linking. Use a disposable client with the test probe from above.

```sh
python3 tools/aurambliss-qa/windows_audit.py prepare minecraft/shaderpacks/AuramBliss /tmp/bliss-windows
python3 tools/aurambliss-qa/run.py "/path/to/PrismLauncher/instances/Auram" "/path/to/test-save" \
  --shader minecraft/shaderpacks/AuramBliss --java "/path/to/native/java" --no-dh \
  --probe /tmp/bliss-probe.jar --lifecycle /tmp/bliss-windows/lifecycle.txt \
  --java-option=-XX:TieredStopAtLevel=1 --timeout 300
python3 tools/aurambliss-qa/windows_audit.py verify /tmp/bliss-windows
```

The export includes the device defines, image declarations, and GLSL for DH, fullscreen composite, sky, terrain, shadow, and compute stages across the three vanilla dimension programs. The checks cover default, feature-heavy compute, and forced compatibility. A device run uses the regular runner with native Java and the same scene, options, and DH configuration. Add `--force-compat` for a second run.

For the shared RF shader path, supply the rebuilt compatibility JAR with `--companion` and add `--java-option=-Dauram.bliss.qa.auditPreparedRf=true` during export.

## Source inventory

Generate the control inventory with `python3 tools/aurambliss-qa/inventory.py minecraft/shaderpacks/AuramBliss /tmp/bliss-options.json`. The output lists profile values, menu paths, source lines, suggested scene checks, dimensions and texture declarations.
