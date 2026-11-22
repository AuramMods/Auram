# AuramPhoton in Auram

Select **AuramPhoton** in Minecraft's shader-pack menu. The pack includes the shader source and its portable Forge companion. Auram uses Minecraft 1.20.1, Forge 47.4.10, Oculus 1.8.0, Embeddium 0.3.31 and Distant Horizons 3.3.3. Launch with a native Java 17 runtime.

On Apple OpenGL, AuramPhoton uses split lighting passes, packed DH depth and raster Colored Lights. On Windows, it selects the regular compute path. The `FORCE_APPLE_COMPAT=true` shader setting selects the raster path on Windows for comparison or for GPUs with a smaller sampler budget. Both paths keep the Photon preset and feature controls. The Windows-conditioned Oculus source audit passed for NVIDIA, AMD and Intel. Native runtime verification used an Apple M4 Max.

The DH configuration keeps Auram's two generation workers, 256-chunk draw radius and internal-server generator. DH 3.3.3 can migrate existing LOD caches. Better Caves 2.0.7 supplies liquid-region data to DH world generation. The pack also carries the [MidnightLib Forge startup backport](../tools/midnightlib-forge-backport/README.md).

Photonics and Voxy have separate provider requirements. The included renderer stack uses Distant Horizons. [Photon license and attribution](../minecraft/shaderpacks/AuramPhoton/LICENSE)
