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

The Apple OpenGL linker reflected 13 samplers for the patched Overworld lighting pass, down from 16. A hidden client with active DH and a depth readback at 854 × 480 found all 409,920 pixels matched the three source textures exactly in the current RGBA32F target. The readback saw one DH-only opaque pixel, no nearer translucent pixels and GL error 0. This forest scene establishes the packing result. Shoreline coverage remains open.

## Feature-heavy sampler coverage

Oculus 1.8.0 also occupies unit 0 for the default color target even if the linked program has no color-target-0 sampler. On the 16-unit Apple context this leaves 13 active user samplers for that program. The front depth and translucency flags now travel with the existing lighting guide. Normal, AO and specular values share one RGBA16 target with eight bits per original component. The preparation pass writes the shared inputs before opaque lighting and the packed channels become available for subsequent passes.

Native hidden Overworld launches with translucent colored shadows, resource-pack sky mode 1, DH shadows, 65% TAA upscaling and DOF mode 5 entered the world with and without DH. The packed lighting program linked with 13 active samplers in both cases. The initial duplicate-uniform experiment also hit a HotSpot C2 compiler crash after Oculus had fallen back. The successful repeat used native Temurin Java 17. LPV coverage follows separately.

## Raster LPV terrain

For compatibility rendering the Forge companion replays Embeddium's shadow terrain into a 16-bit voxel-ID target. Layered fragment draws propagate Bliss's original RGB and sky attenuation through its integer material palette into a pair of RGBA8 volumes. The extra slice stores the handheld colors, allowing all lighting readers to use one sampler. The default Windows compute stages keep their image-backed material and transport path.

Apple OpenGL linked and drew the integer palette for 2,048 IDs with default, candle, and adjusted saturation and redstone settings. The native 64³ client initialized and captured terrain both with and without DH. With DH, colored lighting, volumetric fog illumination, colored candles, colored shadows, resource-pack sky, 65% TAA upscaling and DOF mode 5 enabled, readback found 6,803 lit volume cells, 11 cells at or above 235/255 in one channel, 189 emitting palette entries, and GL error 0. Entity and block-entity voxel capture, larger volumes and compute-path coverage follow separately.

## Rendering coverage to establish

| Area | Runtime observation |
| --- | --- |
| Two shipped profile selections | Compare the default AgX and legacy Hejl tone curves at fixed exposure. Verify the shader GUI exposes the two profile selections and carries through existing instance options. |
| Deferred geometry and materials | Trace the solid and translucent G-buffers, normals, labPBR and particle paths. Compare lit terrain, entities, the held item, alpha layers, POM and emissive surfaces. |
| Sky and weather | Compare sun and moon, shadows, clouds, skybox modes, weather, lightning, seasonal tint and fog in a fixed camera scene. |
| Floodfill colored lighting | Compare seed IDs, occlusion, tint, texture update timing, camera-cell shifts and light falloff at all three volume sizes. Cover terrain, entities, held lights, candles, redstone and optional volumetric fog illumination. |
| Distant Horizons | Read opaque and translucent LOD depths and inspect texture ownership during reload. Compare shoreline and underwater occlusion, shadows, noise, AO, SSS, screen-space reflection, overdraw and cloud or fog overlap. |
| Post-processing | Exercise TAA and its render-scale options, DOF modes, bloom, exposure, tonemapping and debug views in matched scenes. |
| Dimensions and pack integration | Exercise Overworld, Nether, End and the shader's wildcard mappings, with Auram's resource packs, Astral Sorcery sky effects and Thaumaturge Warp Mist where applicable. |
