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

## RF streak preparation

The replay feeds continuous 100 million RF/t routes through AuramCore’s RF field. It holds the camera and clear weather fixed and adds a stone wall and red and cyan stained glass in front of the routes. AuramCore reports 16 active cells with one route, 216 with the 16-route room, and 480 with the 40-route stress fixture. The original inline baseline used three 300-frame windows for each scene. The first A/B used three more 300-frame windows for each backend and scene, then revisited the dense room and removed all routes.

The first candidate cut the measured fragment work. In the dense room, median bloom GPU time went from 0.085 to 0.040 ms and the sharp pass went from 0.163 to 0.099 ms. Render-thread median went from 6.03 to 7.13 ms. The revision combines cache clearing and preparation in one workgroup dispatch. It avoids dispatching for fewer than 64 tracked edges, volumes that cover more than 8,192 cells, and disabled streaks. The fragment passes retain their original depth tests and ray intersections.

These measurements are from the final guarded A/B. Each window contains 400 frames. The table shows the median across windows for each backend and all times are milliseconds. Bloom is `draw/composite7` and sharp streaks are `draw/composite12`.

| Scene | Inline / candidate windows | Frame median | Frame p95 | CPU median | CPU p95 | Bloom GPU median | Sharp GPU median | Voxy shadow GPU median |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Empty | 1 / 1 | 8.48 → 9.06 | 12.99 → 13.34 | 7.58 → 8.30 | 10.08 → 9.76 | 0.000 → 0.000 | 0.035 → 0.032 | 0.176 → 0.185 |
| One route | 2 / 1 | 9.15 → 8.14 | 16.14 → 11.40 | 8.34 → 7.49 | 10.14 → 9.64 | 0.015 → 0.004 | 0.080 → 0.082 | 0.179 → 0.185 |
| Dense | 1 / 1 | 10.32 → 7.43 | 20.57 → 10.09 | 8.90 → 7.10 | 11.37 → 9.23 | 0.085 → 0.043 | 0.159 → 0.095 | 0.180 → 0.173 |
| Stress | 2 / 1 | 9.00 → 8.47 | 16.49 → 14.58 | 8.38 → 7.95 | 10.08 → 9.74 | 0.140 → 0.077 | 0.251 → 0.152 | 0.184 → 0.184 |

The frame and CPU distributions varied substantially between runs. The stress scene’s bloom p95 was 0.160 → 0.092 ms and sharp p95 was 0.325 → 0.205 ms. In the earlier single-dispatch stress check, 400-frame p95 was 7.49 → 7.39 ms with two windows per backend. The consistent result was less GPU time in the two fragment passes and overlapping end-to-end stress results. The one-route revisit in that check exposed a frame p95 increase from 6.77 to 8.58 ms when preparation ran on a single route. The retained guard bypasses that dispatch.

GLA reported zero for the preparation dispatch in OpenGL elapsed queries. A separate Metal trace disabled LPV and Voxy rendering while keeping the 40-route fixture active. It measured the repeating RF compute command at 0.038 ms median and 0.044 ms p95 over 879 spans. The frame and render-thread timings above include the dispatch, binding, and synchronization costs.

The final replay read back 224 reserved pulse slots in the dense room and 512 in stress, with both fragment programs reporting the prepared path. Stress returned 512 again after shader reload. Disabling streaks returned zero and re-enabling restored 224. An empty field, one route, the one-route revisit, and removal of all routes bypassed preparation. Paths and materials matched when the shader and circuit clocks stayed frozen across reloads. The absolute image difference averaged 0.46 out of 255 in the dense view and 0.47 in stress. In the streak region it peaked at six channel steps in both scenes.

The guarded implementation was retained in `04908eb`. First-time installation requires the rebuilt compatibility JAR and a restart. With that JAR installed, changes to RF shader source and options use ordinary shader reloads. The filesystem contains the raw test runs under `rf-inline-baseline`, `rf-prepared-ab-2`, `rf-prepared-revision`, and `rf-prepared-adaptive` in the benchmark cache.

## Crystal-chest item meshes

The clear-weather replay places a wall at Z 40 and views it from 48.5, 82, 23.5 with yaw 0 and pitch 7°. The loaded wall has seven different vanilla items per chest. Every mixed fixture has glass, Iron Chests items and enchanted books alongside ordinary items. The server repopulates and checks the client’s display inventory before every case.

The initial stock reproduction measured approximately 36 ms median per frame with 512 loaded chests and 10 ms with 512 empty chests. A 120-second JFR extract collected 7,050 render-thread samples. Of the 3,941 samples with crystal-chest item rendering on the stack, 1,866 ended in Oculus’s extended-vertex `endQuad` and 1,036 ended in `NormalHelper.computeTangent`. An object-level mesh replay exercised the renderer but continued to rebuild the extended vertices. The retained production path records the original packed quads for eligible meshes and submits each item as a single batch using that draw’s position, orientation, light, overlay, and shader IDs. Animated textures use their original atlas, quad ordering follows the original submission order, and resource reload clears the local cache. Items with color providers, custom models, tags, or foil use the original renderer, as do meshes with non-axis-aligned normals or tangents and incompatible vertex formats.

These are the medians of three alternating 300-frame windows per backend for the first packed implementation. The corrected build was benchmarked separately below. Times are in milliseconds. The GPU world column includes the entire world draw group measured by the probe. The Voxy shadow column shows that separately measured contribution.

| Scene | Frame median | Frame p95 | CPU median | CPU p95 | World GPU median / p95 | Voxy shadows GPU median | Vertex-buffer draws per frame |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 512 empty | 21.02 → 22.25 | 32.42 → 34.36 | 14.10 → 14.14 | 16.06 → 15.72 | 12.46 / 23.13 → 11.34 / 23.02 | 0.189 → 0.210 | 22 / 22 |
| 64 loaded | 26.97 → 21.42 | 39.02 → 31.17 | 18.37 → 11.97 | 20.98 → 14.06 | 15.40 / 25.50 → 5.95 / 19.84 | 0.195 → 0.203 | 26 / 26 |
| 512 loaded | 32.89 → 23.13 | 40.73 → 28.36 | 31.72 → 21.59 | 37.36 → 26.43 | 17.54 / 21.22 → 12.35 / 14.70 | 0.214 → 0.207 | 26 / 26 |
| 512 mixed | 40.88 → 33.72 | 49.53 → 39.34 | 39.31 → 32.37 | 45.72 → 33.81 | 21.81 / 26.04 → 17.88 / 20.44 | 0.213 → 0.200 | 28 / 28 |

Other workload on the Mac contributed to substantial frame-time spread. The empty runs served zero items from the cache. Every packed 512-loaded median frame and CPU result beat every stock median. Individual packed p95 runs ranged from 26.23 to 42.70 ms, with stock ranging from 34.22 to 41.30 ms. The cache served 7,168 item renders per frame in the loaded scene and 5,632 in mixed, counting world and shadow rendering. Three shadow vertex-buffer draws per frame were observed in both 512-loaded modes and four in both mixed modes. Matched screenshots showed the same chest contents, glass, and shadows. Mean channel difference in the loaded screenshot was 1.33 out of 255.

The original replay reached its time limit after the fixed-camera cases and inventory change. A shorter replay with the initial JAR moved the camera seven blocks east while turning 17° in each mode. Across one 200-frame window per mode, frame p95 was 85.85 → 73.69 ms and CPU median was 61.95 → 48.73 ms while a separate full-pack client was starting. Both runs reported 28 vertex-buffer draws per frame and zero GL errors. The resource reload completed successfully and the changed chest still displayed emerald. The next 200-frame window averaged 5,626 cached item renders per frame.

The initial packed mesh implementation is in `7badb6c`. The updated build in `322b5d4` requires the rebuilt companion JAR and a client restart.

The full-pack replay found that Flerovium draws eligible models before the standard item renderer sees them. The original hook received Flerovium’s placeholder model and served zero cached meshes. The follow-up captures Flerovium’s own packed vertices and keeps its original face mask as part of the cache key. Reviewing both vertex formats also exposed reversed light and overlay offsets in the original Iris capture. The follow-up uses the offsets reported by each format and matches each renderer’s original method for combining baked and per-instance light.

Three new 240-frame windows per backend with the corrected build and 512 loaded chests on Iris measured frame median 34.69 → 25.84 ms, frame p95 39.69 → 28.49 ms, CPU median 33.36 → 24.49 ms, and CPU p95 36.28 → 26.78 ms. World GPU median/p95 was 18.28/20.66 → 14.08/15.73 ms. Voxy shadow median was 0.211 → 0.196 ms. Each backend averaged 26 vertex-buffer draws per frame. The cache served 7,168 item renders per frame, and the matched capture’s mean channel difference was 1.36 out of 255.

The corrected build also completed the moving-camera and resource reload replay. The 240-frame moving windows measured p95 82.62 → 63.90 ms and CPU median 56.97 → 41.08 ms. Both averaged 27 vertex-buffer draws. The same altered chest displayed emerald before and after reloading resources. The next window served 7,156 cached item renders per frame and reported zero GL errors.

An initial nighttime pair showed a p95 increase on both renderers. The Iris repeat held the camera and animation phase fixed with the sea lanterns lit, and ran three alternating 180-frame windows per backend. Every cached window beat every stock window. Frame median was 32.01 → 22.47 ms, frame p95 was 41.55 → 27.18 ms, and CPU median was 31.00 → 21.38 ms. World GPU median was 17.22 → 12.47 ms and Voxy shadow median was 0.200 → 0.207 ms. Both averaged 27 vertex-buffer draws, the cache served 7,168 item renders per frame, and the matched screenshots differed by an average of 0.93 out of 255 per color channel.

With Flerovium present, the first 512-chest and mixed comparisons were within run variation. The stronger follow-up added a second 512-chest bank. Across three 240-frame windows per backend, frame median was 17.73 → 17.08 ms, frame p95 was 21.37 → 20.90 ms, CPU median was 16.81 → 16.10 ms, and CPU p95 was 18.55 → 17.95 ms. Both averaged 26 vertex-buffer draws per frame, and the cache served 14,336 item renders per frame. At the original 512-chest workload, cache frame p95 values of 27.11 and 29.44 ms fell within the stock range of 17.64–30.79 ms. The captured daylight and sea-lantern-lit views showed the same transparency, contents, and lighting. Mean channel differences were 2.17 in the doubled scene and 0.97 in the night scene, out of 255.

## Windows shader source audit

Oculus 1.8.0 exported the Windows OpenGL 4.6 sources for NVIDIA, AMD, and Intel. The matrix covered default, compute, and forced-compatibility configurations with Distant Horizons, plus two NVIDIA cases without it. `glslang` 16.6.0 linked 821 graphics programs and 24 compute stages with zero failures across the 11 cases. All cases included the prepared RF bloom and sharp fragment programs in the three vanilla dimension variants. The companion RF compute source also linked independently under GLSL 4.30 on the Mac path and GLSL 4.60 on the Windows path. Windows GPU runtime remains untested.

## Combined Auram replay

The final hidden macOS full-pack client used the rebuilt companion from `322b5d4`, the pack’s Flerovium configuration, and Neo-Voxy. The Overworld scene ran 128³ compute FloodFill, 16 RF routes, and 512 loaded crystal chests together. The cache served 7,168 item renders per frame and both RF consumers used the 224 reserved slots. The same counts returned after a shader reload, with 27 vertex-buffer draws per frame and zero GL errors. Stopping RF dropped the prepared slots and consumer counts to zero. The earlier full-pack smoke also opened the Nether and captured it with Bliss active.

The full-pack night check alternated three 90-frame windows per backend at a fixed animation phase with the sea lanterns lit. The frame median was 17.16 → 17.07 ms, frame p95 20.97 → 21.37 ms, and CPU median 12.91 → 13.29 ms. The p95 change was smaller than the 0.54 ms spread of the stock runs. Both backends averaged the same vertex-buffer draw count. The doubled fixture’s lower median frame time and CPU time, the overlapping 512-chest repeat ranges, and the full-pack activation and visual checks supported retaining the Flerovium path.

The tested `auramphoton-compat-0.1.0-dh333.jar` has SHA-256 `f77bce953fdf6345d4ef4a212e78d15b44c0ce52a5ef64e95c8461195640c3a1`. The benchmark archive includes the combined run’s per-pass timings, lifecycle log, and matched captures under `full-pack`. The live baseline was recorded separately before installing these changes in the disposable profiles.
