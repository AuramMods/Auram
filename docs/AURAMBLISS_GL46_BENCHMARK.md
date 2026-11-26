# AuramBliss OpenGL 4.6 rendering experiments

## Test profile

The tests ran on an Apple M4 Max with macOS 27.0.1, GLA 1.0.1 reporting OpenGL 4.6.0 Metal, Java 17, and Forge 47.4.10 for Minecraft 1.20.1. The disposable profile used Embeddium, Oculus 1.8.0, Neo-Voxy 0.4.17-beta.1, AuramCore 1.0.59, Forge Connector, and the Fabric API required by GLA. The GLFW window stayed hidden and unfocused. Sound, narrator, and accessibility onboarding were disabled.

The FloodFill fixture used a copy of a cached Overworld plateau. The camera began at 48.5, 81, 22.5 with yaw 0 and pitch 10°. The fixture fixed the weather and daylight, enabled FloodFill and its fog illumination, and used a 1280×720 framebuffer, eight-chunk vanilla render distance, and no VSync. For timing, distant generation was disabled and shader changes were followed by a warm-up. The sparse scene contains a sea lantern, shroomlight, tinted glass, an occluding block, and an offhand soul torch. The dense scene adds 936 emitters, red and cyan glass, a stone barrier, and 16 synthetic routes delivered through AuramCore at 100 million RF/t. The readback counted 216 RF cells and 416 face rates. The motion check moved the camera 22 blocks east and 8 blocks north, with a 32° yaw turn.

## FloodFill propagation

The first compute prototype used 8×8×8 workgroups. Three 180-frame windows per backend and scene caught a sparse 256³ p95 regression from 10.12 to 16.88 ms. Its replacement uses 8×8×4 workgroups and 12,000 bytes of shared storage for the center and six face halos. It skips the viewport and framebuffer state changes needed for layered raster propagation.

The table reports medians of the three 300-frame windows per backend. The sparse 128³ row also includes a fourth window after the dense work was cleared out. All values are in milliseconds. “CPU” measures the render thread.

| Scene | Frame median, raster → compute | Frame p95, raster → compute | CPU median, raster → compute | CPU p95, raster → compute | Raster p95 spread |
| --- | ---: | ---: | ---: | ---: | ---: |
| Sparse 64³ | 9.35 → 6.37 | 13.21 → 12.65 | 6.28 → 5.79 | 8.20 → 7.71 | 4.86 |
| Sparse 128³ | 8.17 → 7.60 | 12.69 → 12.16 | 6.67 → 6.43 | 8.14 → 8.29 | 2.20 |
| Sparse 256³ | 10.27 → 9.22 | 18.72 → 14.15 | 8.55 → 8.10 | 10.48 → 9.98 | 2.71 |
| Dense 64³ | 9.76 → 9.79 | 14.86 → 15.55 | 8.21 → 8.22 | 10.04 → 9.78 | 4.11 |
| Dense 128³ | 10.14 → 9.44 | 14.81 → 15.98 | 8.63 → 7.87 | 10.33 → 9.66 | 4.30 |
| Dense 256³ | 11.93 → 10.46 | 21.02 → 18.03 | 8.79 → 8.19 | 10.73 → 9.89 | 8.30 |

In the sparse 256³ scene, every revised compute window beat every raster window for both median and p95. The smaller volumes had overlapping runs. In the fourth sparse 128³ pair, median moved from 9.09 to 10.28 and p95 moved from 12.70 to 15.18. Three further 480-frame dense 64³ windows per backend produced median 7.46 → 8.47 and p95 9.71 → 13.25, with a 6.52 ms raster p95 spread and a final compute p95 of 7.46 ms. The runs establish a 256³ gain and show significant small-volume timing variability.

Raster GPU queries measured sparse 256³ propagation at 1.229 ms median and 1.514 ms p95, occupancy at 0.203 ms median, and Voxy shadows at 0.160 ms median. The first compute candidate’s occupancy and Voxy shadow medians in the same scene were 0.251 and 0.173 ms. GLA returned zero for compute with both OpenGL elapsed queries and timestamp pairs. Separate Metal System Traces with Voxy rendering disabled isolated the revised 256³ dispatch at 1.312 ms median and 1.460 ms p95 across 1,069 GPU spans. The equivalent raster trace contained blits and no compute encoder for propagation.

The revised sparse light readbacks differ by at most one 8-bit channel step at all three sizes. The handheld palette matched byte for byte, including after moving the volume and reloading. In the frozen 128³ dense comparison, the mean channel difference was 0.010 out of 255 for the volume and 0.65 out of 255 for the screenshot. The compute volume matched byte for byte before and after a shader reload. The moving comparison measured 189,096 raster and 189,577 compute pixels with packed opaque and front Voxy depth, with zero GL errors. The server spawned a Blaze and Oculus confirmed capturing its shadow entity draw. Removing RF routes returned the active cell and rate counts to zero.

The GLSL 4.10 raster and GLSL 4.30 compute companions passed glslang 16.6.0 for macOS, macOS with RF and fluid injection, and forced Windows compatibility. Windows runtime and timings were not measured.

The revised backend was retained in `2535f7c`. The 256³ cases improved and the smaller volumes remained within the variation of the raster runs after retesting. macOS selects it when compute dispatch, image binding, barriers, and the required workgroup and image resources are available. Raster remains the fallback. First-time installation requires a client restart with the rebuilt companion JAR. The isolated default-selection smoke test started and reloaded at 128³ with compute active, no GL errors, and at most one byte step of readback variance.

The raw logs, volume dumps, and full traces are in `~/Library/Caches/AuramGL46Rendering`. Compare `lpv-matrix-ab`, `lpv-matrix-v2`, and `lpv-v2-checks` with the corresponding lifecycle files. Compact results, lifecycle files, and representative screenshots are in `~/Documents/Auram Benchmarks/2026-11-26-auram-gl46`. The earlier live client baseline remains in `~/Documents/Auram Benchmarks/2026-11-26-live-bliss.y7vUR9`.
