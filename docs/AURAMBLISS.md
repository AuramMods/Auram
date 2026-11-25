# AuramBliss (Chocapic13' Shaders edit)

AuramBliss is Auram's port of Bliss v2.1.2 by X0nk, based on Chocapic13's shaders. The in-game folder name is `AuramBliss`. See the [license](../minecraft/shaderpacks/AuramBliss/LICENSE.md), [contributor credits](../minecraft/shaderpacks/AuramBliss/CREDITS.txt), [Bliss release source](https://github.com/X0nk/Bliss-Shader/tree/a0e48db09cdd1f2a7a39dd9eec5d54700486cbda), and [Chocapic13's Shaders](https://www.curseforge.com/minecraft/customization/chocapic13-shaders).

Auram commit `bdd9d03` imported 344 runtime files. All 344 match upstream release `a0e48db09cdd1f2a7a39dd9eec5d54700486cbda` after normalizing CRLF line endings. The six omitted upstream files are in `shaders/template/` and generate block, item and entity properties. The tracking branch began from Auram build 327 plus the two existing pack commits through `4a8fa22`.

## Baseline

A disposable Overworld save ran with Minecraft 1.20.1, Forge 47.4.10, Oculus 1.8.0 and Embeddium 0.3.31. The Apple M4 Max reported OpenGL 4.1 Metal 91.7. Test windows were hidden and sampled for focus, with master volume zero, narrator off and accessibility onboarding off. Oculus shader debugging exported the transformed GLSL.

| Variant | Observed result |
| --- | --- |
| Default, without DH | Loaded AuramBliss, entered the test world and captured rendered frames. |
| Default, with DH 3.3.3 | Oculus fell back while building Overworld `composite2`, whose fragment body is `dimensions/composite1.fsh`. Apple OpenGL linked the exported program with 16 active samplers. Oculus reserves units 1 and 2 in composite programs and uses `GL_MAX_TEXTURE_IMAGE_UNITS` as its allocation ceiling. The bind of `depthtex0` threw `No more available texture units`. |
| LPV enabled at 64³, without DH | Oculus fell back in `GlImage.setup` when LWJGL called the unavailable `glClearTexImage` entry point. This happens before compiling a compute program or performing voxel writes. |

## Depth packing on Apple OpenGL

The compatibility path stores vanilla opaque, DH opaque, and DH translucent depth in the three unused color channels of colortex12. Its alpha channel keeps the upstream mixed-depth encoding at binary16 precision. The main opaque lighting pass reads the packed depths for screen-space rays and water reconstruction. The Windows default path uses the original depth samplers. The **Force compatibility rendering** option selects the packed path on Windows.

The Apple OpenGL linker reflected 13 samplers for the patched Overworld lighting pass, down from 16. A hidden client with active DH and a depth readback at 854 × 480 found all 409,920 pixels matched the three source textures exactly in the current RGBA32F target. The readback saw one DH-only opaque pixel, no nearer translucent pixels and GL error 0. The shoreline, underwater, and full-client counts are recorded below.

## Voxy surface handoff

When regular chunks have drawn a deeper surface but the section in front of it is still arriving, the opaque pass keeps the foreground Voxy mesh. Once that near section is ready, a deeper near surface can show through a real opening. The existing dither still blends surfaces that agree within 0.6 blocks along the view ray.

Voxy clears its own depth at pixels marked by the near terrain stencil. AuramBliss writes Voxy-owned opaque depth into a single-channel color target so lighting, ambient occlusion, and fog can use the same position as the visible material. The packed compatibility path records the near depth as absent when the Voxy surface covers it.

A disposable three-chunk Overworld test on macOS with GLA, Embeddium, Oculus, and Neo-Voxy 0.4.0-beta.1 hid a rectangle of the near roof to simulate a delayed section. The original shader showed the lower floor. The patched shader hid the floor behind the cached roof with matching surface lighting while the section was marked as loading, then exposed the floor when the section was marked ready. A separate opening in the roof stayed visible in both states.

## Feature-heavy sampler coverage

Oculus 1.8.0 also occupies unit 0 for the default color target even if the linked program has no color-target-0 sampler. On the 16-unit Apple context this leaves 13 active user samplers for that program. The front depth and translucency flags now travel with the existing lighting guide. Normal, AO and specular values share one RGBA16 target with eight bits per original component. The preparation pass writes the shared inputs before opaque lighting and the packed channels become available for subsequent passes.

Native hidden Overworld launches with translucent colored shadows, resource-pack sky mode 1, DH shadows, 65% TAA upscaling and DOF mode 5 entered the world with and without DH. The packed lighting program linked with 13 active samplers in both cases. The initial duplicate-uniform experiment also hit a HotSpot C2 compiler crash after Oculus had fallen back. The successful repeat used native Temurin Java 17. The raster LPV extends that combination with colored block and entity lights.

## Raster LPV terrain

For compatibility rendering the Forge companion replays Embeddium's shadow terrain into a 16-bit voxel-ID target. Layered fragment draws propagate Bliss's original RGB and sky attenuation through its integer material palette into a pair of RGBA8 volumes. The extra slice stores the handheld colors, allowing all lighting readers to use one sampler. The default Windows compute stages keep their image-backed material and transport path.

Apple OpenGL linked and drew the integer palette for 2,048 IDs with default, candle, and adjusted saturation and redstone settings. The native 64³ client initialized and captured terrain both with and without DH. With DH, colored lighting, volumetric fog illumination, colored candles, colored shadows, resource-pack sky, 65% TAA upscaling and DOF mode 5 enabled, readback found 6,803 lit volume cells, 11 cells at or above 235/255 in one channel, 189 emitting palette entries, and GL error 0. Native timings for 64³, 128³ and 256³ are recorded below.

The companion replays Oculus’s shadow entity batch from the vertex buffer’s real position and ID attributes. It preserves the loader’s render phase and applies Bliss’s existing light classification. A 64³ native test with DH observed eight Blaze voxels, one dropped sea-lantern voxel and one beacon voxel in the current occupancy target, with GL error 0.

## Native validation

The [isolated replay and probes](../tools/aurambliss-qa/README.md) ran with native Temurin 17 on an Apple M4 Max using OpenGL 4.1 Metal 91.7. All measured window snapshots reported GLFW visibility and focus at zero. The full Auram build 336 client loaded Forge 47.4.10, Oculus 1.8.0, Embeddium 0.3.31, DH 3.3.3 and AuramCore 1.0.25.

| Scene | Result |
| --- | --- |
| Cached shoreline, 854 × 480 | 55,105 DH opaque pixels and 55,920 nearer DH translucent pixels where vanilla opaque terrain was absent. All 409,920 packed pixels matched the three sampled source textures. |
| Fresh DH database, 854 × 480 | With `FEATURES` generation enabled, DH reported about 127 chunks per second during the middle of the run. The new database held 1,751 FullData rows. The probe counted 54,525 DH-only opaque and 56,161 nearer translucent pixels. All 409,920 packed pixels matched. |
| Underwater, 854 × 480 | Confirmed a submerged camera from Minecraft’s actual fluid state. The packed depth matched at all 409,920 pixels, including 13,738 DH opaque pixels beyond the vanilla opaque surface. |
| Full client, 1280 × 720 | With 65% TAA upscaling and 128³ LPV, the client rendered the shoreline, toggled Warp Mist, switched both tone curves, entered the Aether and returned. The loaded resource pack reported labPBR. The probe matched all 389,376 active pixels in the 832 × 468 render viewport after return. |
| Fullscreen | A virtual 1920 × 1080 display attached successfully. The client returned to 854 × 480 windowed rendering. DH and labPBR remained active and the window stayed hidden and unfocused. |
| Resize and reload | The heavy combination resized to 1040 × 584, changed GUI scale, reloaded resources and toggled DH off and on. Photon and BSL both rendered before switching back to Bliss. The probe matched all 256,880 active pixels after the 65% viewport resize. |
| Astral Sorcery | The replay scheduled real solar and lunar events from Astral’s calendar. Bliss observed 50% solar progression and 100% lunar coverage through live uniforms. The replay captured the sky before and at peak coverage. |

All texture and lifecycle probes in the table reported GL error zero. The stock pack keeps DH rendering off in its common configuration. Turn on DH rendering to use the shader’s LOD paths.

## Feature parity matrix

| Feature | Implementation and coverage |
| --- | --- |
| Presets and settings | The AgX default and legacy Hejl profile both render at runtime. The generated inventory lists 408 menu controls and maps the controls to shader references and applicable scenes. Upstream reserved `Snowy_Winter` and unused `Exposure_Speed` retain their imported IDs. |
| Geometry and materials | The normal, AO and labPBR channels carry their packed values on the compatibility path. Native replay loaded labPBR with parallax occlusion mapping enabled. Oculus’s translucent entity and block passes follow the existing shader menu switch. |
| Shadow, light, and fog | The translucent colored-shadow, opaque-shadow, entity and terrain paths execute together. LPV retains the palette-scale, size, falloff, occlusion, hand-light and fog controls. Native readback compared palette entries and observed live terrain, held-light inputs and real entity capture. |
| Water, reflection, and DH | Retains the original DH terrain, water, shadow, reflection and fog branches. Native replay measured actual opaque and translucent attachments across a shoreline and the opaque attachment underwater. The source matrix linked all three dimension programs with default DH AO, SSS, screen-space reflections, noise and overdraw prevention, plus heavy DH shadows. |
| Sky, weather, and climate | The two cloud terrain modes work with DH and native OpenGL. Astral’s resource-pack sky geometry and procedural eclipses feed Bliss’s original sky, sun and moon paths. Warp Mist covers vanilla distance-based fog as well as the DH opaque and translucent layers. Weather and seasonal parameters remain public, with source-level coverage. |
| Post-processing and dimensions | Tested 65% TAA upscaling, DOF mode 5, both tone curves, Overworld, Nether, End, the Aether wildcard, and world re-entry. On the matched default midday scene, the average absolute RGB-channel difference from the upstream framebuffer was 2.98 / 2.36 / 2.22 on the 0–255 scale. Clouds and temporal sampling contribute to frame variation. |

The compatibility path stores 8-bit pairs in a 16-bit channel, which changes auxiliary surface quantization from the separate original buffers. It preserves the material channels and layout. The upstream profile, source audit, and PNG comparisons give an A/B reference for reports.

## Frame-time measurements

All measurements use 90 frames at 854 × 480, an eight-chunk vanilla view and a 90 FPS cap. Heap is the high-water value of the Java heap across the sampling window. Cold and cached results use the same 128³ shoreline route, moving 22 blocks in X, −8 in Z and turning 32°. The reference route uses the pinned release and no DH, since the original does not load with DH or LPV on the test Apple driver.

| Scene | Median / p95 / p99 frame ms | LPV GPU median / p95 ms | Heap GiB |
| --- | ---: | ---: | ---: |
| Pinned default, no DH, matched moving view | 11.50 / 15.07 / 22.33 | — | 0.77 |
| AuramBliss default, no DH, matched moving view | 11.75 / 12.80 / 15.67 | — | 0.94 |
| Cached DH, 128³ LPV, matched moving view | 11.56 / 13.37 / 18.21 | 0.93 / 1.02 | 1.67 |
| Cold DH, 128³ LPV, matched moving view | 11.45 / 13.44 / 16.18 | 0.98 / 1.01 | 1.61 |
| Static 64³ LPV, no DH | 13.99 / 28.10 / 57.99 | 0.28 / 0.52 | 1.43 |
| Static 128³ LPV, no DH | 13.85 / 20.85 / 39.80 | 1.02 / 1.08 | 1.34 |
| Static 256³ LPV, no DH | 19.60 / 28.48 / 43.25 | 2.80 / 3.18 | 1.48 |

The M4 Max target for this isolated replay is under 25 ms median, under 33.3 ms p95 and under 4 ms p95 for GPU propagation. Every sample set met those targets. The 256³ volume and isolated startup can exceed the 33.3 ms budget at p99. Isolated shader reloads measured roughly 2.2 seconds, and full-client tone curve reloads measured 2.5–2.6 seconds with DH, labPBR and 128³ LPV.

## Packaged client

An archive of the candidate Minecraft tree included the shader files and companion JAR from the pack. The SHA-256 of `auramphoton-compat-0.1.0-dh333.jar` was `22ac4db7979d1e30b036ee3ccd025b94c80eaef38bb89a1c4acc1850a7670c99` in both the prepared archive and the disposable client.

The 1280 × 720 heavy launch used 128³ LPV and 65% TAA upscaling. The probe observed 13,170 DH-only opaque pixels and 4,044 nearer DH translucent pixels in the active viewport and verified all 389,376 packed depth pixels with GL error zero. The full-pack 90-frame sample measured 30.70 / 44.95 / 162.03 ms at median / p95 / p99. GPU propagation measured 1.22 / 1.85 / 2.64 ms, with 9.43 GiB peak sampled Java heap. The sample starts shortly after the full client enters the world.

The first fresh-copy launch encountered an `ArrayIndexOutOfBoundsException` in fastutil during the initial loading overlay before Oculus loaded the shader. The repeated launch used the same pack files and completed the renderer and depth replay above.

## Windows coverage

Oculus 1.8.0 generated and glslang linked 821 graphics programs and 24 compute stages across 11 NVIDIA, AMD and Intel cases. This enumerated Overworld, Nether and End, native compute selection, forced compatibility, default settings and no-DH samples. Every compute case declared four images. Forced compatibility declared zero images. The heavy runs enabled colored shadows, volumetric LPV, terrain-independent clouds, translucent entities, POM, 65% TAA upscaling and DOF 5. Native Windows hardware remains untested. The same disposable client replay accepts a native Windows Java executable and can be repeated with the forced compatibility switch.

## Voxy and Embeddium sunlight shadows

AuramBliss's Overworld pass accepts opaque and alpha-tested Voxy casters in Oculus's existing sunlight map. Voxy submits them before Oculus saves its opaque shadow depth. Both caster paths use the same Bliss projection, distortion and depth compression. The existing receiver lookup, texel alignment, shadow bias, filtering, contact shadows and cloud shading apply to both renderers. Oculus continues to render translucent and entity casters.

Voxy traverses the light frustum independently of the eye frustum, within 768 horizontal blocks of the camera and the available Voxy terrain radius. Each shadow pass can select up to 16,384 mesh sections and request up to 32 missing descendants. Sections submitted by Embeddium's opaque or cutout shadow pass take precedence over their Voxy copies. The receiver coverage still follows Bliss's shadow distance and fade. The validation preset uses 1,024 shadow texels and a 128-block shadow distance. A caster can be much farther away than its receiver when the sun is low. Coarse LOD geometry can change silhouette detail.

### Test positions

Use the disposable Overworld fixture with daylight and weather cycles frozen, clear weather, and sun path rotation 0°. The white concrete receiver extends from X −128 to 128 and Z 240 to 335 at Y 192. The off-screen island is about 540 blocks west of the receiver, with a raised cap between X −545 and −516, Z 256 and 307, and Y 285 and 290. The two near stone posts reach Y 205 and 215 and the Voxy leaf canopy spans X 73 to 84.

- Sunset tick 12200. Use `/tp @s 8.5 278 287.5 0 89` and reopen the world at that pose for the cold off-screen test. Use `/tp @s 18.5 242 291.5 0 89` to see the near and Voxy receivers in the same frame.
- Daylight tick 9000. Use `/tp @s 13.5 246 282.5 -90 45` to see the near posts and the Voxy canopy.
- Near caster test at tick 11200. Use `/tp @s 8.5 255 287.5 0 89` to follow the taller near post's shadow toward the Voxy receiver.
- Movement test at tick 9000. Start at `/tp @s 4.5 229 281.5 -90 38` and fly east for four seconds, moving the terrain handoff across the receiver.

The background, muted 920 × 600 client ran Forge 47.4.10, GLA 1.0.1, Embeddium 0.3.31 and Oculus 1.8.0 on an Apple M4 Max. At three chunks, the leaf canopy reduced the median brightness of the receiver under it by 80 levels on an 8-bit grayscale scale, with a neighboring unshadowed control changing by 0.5 levels. The off-screen island's sunset shadow continued across the terrain handoff and appeared following a fresh launch at the downward camera pose. A temporary receiver overlay identified Embeddium and Voxy pixels at the lower camera pose. A continuous dark region crossed their boundary with Voxy submission enabled and disappeared when the Voxy casters were disabled. The near posts still cast shadows with either setting.

### Frame times

Each row gives the median, 95th and 99th percentile milliseconds across 480 frame swaps after the fixture warmed up. VSync was off in the isolated profile. The caster-pass toggle uses the same candidate jar and shader settings, removing only the optional Voxy shadow program between samples.

| Fixture | Published 0.4.0-beta.1 | Candidate, caster pass disabled | Candidate, caster pass enabled |
| --- | ---: | ---: | ---: |
| Three chunks, daylight | 6.13 / 10.43 / 14.05 | 7.57 / 16.02 / 21.65 | 8.95 / 18.31 / 21.98 |
| Three chunks, sunset | 7.02 / 12.02 / 18.67 | 7.00 / 12.23 / 17.21 | 8.86 / 17.84 / 22.88 |
| Five chunks, daylight | 6.25 / 11.57 / 18.48 | 5.15 / 8.13 / 10.62 | 6.47 / 10.07 / 15.48 |
| Five chunks, sunset | 6.54 / 11.73 / 16.27 | 5.71 / 9.56 / 13.13 | 5.92 / 9.28 / 14.95 |
| Five chunks, moving through the handoff | 5.86 / 7.94 / 10.09 | 6.09 / 9.65 / 12.12 | 8.66 / 17.80 / 22.56 |

Following multiple shader reloads and a Nether round trip, the three-chunk sunset pose recorded 5.72 / 11.20 / 16.77 ms. These runs measure the whole client during each route, including temporal variation and background chunk activity.

### Lifecycle and pack smoke test

The shadow pass rebuilt after shader reloads, a Nether round trip, and saving and rejoining the Overworld in the same JVM. The exact production Forge jar entered the copied full Auram pack, rendered the daylight and canopy fixture with AuramBliss active, and reloaded the shader. Both Distant Horizons rendering switches were disabled. Prism launched the test clients in the background with sound muted. Native Windows OpenGL was unavailable for runtime validation.
