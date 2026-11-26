package dev.auram.photon.compat.mixin;

import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = TransformPatcher.class, remap = false)
abstract class IrisRfShaderVersionMixin {
    @ModifyVariable(method = "patchComposite", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private static String auramBliss$storageBlockVersion(String fragment) {
        if (fragment == null || !fragment.contains("buffer RfPreparedCache")) return fragment;
        // Keep Oculus's legacy transforms while enabling the storage block before parsing.
        return fragment.replaceFirst("(?m)^\\h*#version\\h+120\\h*$", "#version 430 compatibility");
    }
}
