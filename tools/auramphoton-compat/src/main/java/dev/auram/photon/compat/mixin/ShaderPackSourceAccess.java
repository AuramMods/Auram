package dev.auram.photon.compat.mixin;

import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Function;

@Mixin(value = ShaderPack.class, remap = false)
public interface ShaderPackSourceAccess {
    @Accessor("sourceProvider")
    Function<AbsolutePackPath, String> auramPhoton$sourceProvider();
}
