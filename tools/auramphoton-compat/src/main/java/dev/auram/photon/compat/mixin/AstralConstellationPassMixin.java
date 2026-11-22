package dev.auram.photon.compat.mixin;

import dev.auram.photon.compat.AstralConstellationPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Pseudo
@Mixin(targets = "hellfirepvp.astralsorcery.client.sky.constellation.SkyConstellationRenderer", remap = false)
abstract class AstralConstellationPassMixin {
    @Inject(method = "renderSkyConstellations", at = @At("HEAD"))
    private static void auramPhoton$beginConstellations(CallbackInfo ci) {
        AstralConstellationPass.enter();
    }

    @ModifyArg(method = "renderSkyConstellations", at = @At(value = "INVOKE", target =
            "Lhellfirepvp/astralsorcery/client/util/RenderUtil;draw("
            + "Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;Lcom/mojang/blaze3d/vertex/VertexFormat;"
            + "Ljava/util/function/Supplier;Ljava/util/function/Consumer;)V"), index = 2)
    private static Supplier<?> auramPhoton$routeToPhotonSky(Supplier<?> original) {
        return AstralConstellationPass.shaderSupplier(original);
    }

    @Inject(method = "renderSkyConstellations", at = @At("RETURN"))
    private static void auramPhoton$endConstellations(CallbackInfo ci) {
        AstralConstellationPass.leave();
    }
}
