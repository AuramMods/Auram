package dev.auram.photon.compat.mixin;

import dev.auram.photon.compat.lpv.RasterLightVolume;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.gl.texture.TextureAccess;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.samplers.IrisSamplers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisSamplers.class, remap = false)
abstract class IrisLightVolumeSamplerMixin {
    @Inject(method = "addCustomTextures", at = @At("HEAD"))
    private static void auramPhoton$addLightVolume(SamplerHolder samplers,
            Object2ObjectMap<String, TextureAccess> textures, CallbackInfo callback) {
        if (samplers.hasSampler("auram_photon_light")) {
            samplers.addDynamicSampler(TextureType.TEXTURE_3D, RasterLightVolume::activeTexture, null, "auram_photon_light");
        }
    }
}
