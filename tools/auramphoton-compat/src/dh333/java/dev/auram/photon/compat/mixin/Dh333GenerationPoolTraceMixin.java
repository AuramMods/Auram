package dev.auram.photon.compat.mixin;

import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import dev.auram.photon.compat.Dh333GenerationTrace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil", remap = false)
abstract class Dh333GenerationPoolTraceMixin {
    @Shadow private static boolean cameraMovingFast() { throw new AssertionError(); }
    @Shadow private static boolean chunkBuilderOverloaded() { throw new AssertionError(); }

    @Redirect(method = "worldGenThreadsCanRun", at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/util/threading/ThreadPoolUtil;cameraMovingFast()Z"))
    private static boolean auram$cameraGate() {
        boolean defer = cameraMovingFast();
        if (defer) Dh333GenerationTrace.cameraDeferred();
        return defer;
    }

    @Redirect(method = "worldGenThreadsCanRun", at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/util/threading/ThreadPoolUtil;chunkBuilderOverloaded()Z"))
    private static boolean auram$lodBuilderGate() {
        boolean defer = chunkBuilderOverloaded();
        if (defer) Dh333GenerationTrace.lodDeferred();
        return defer;
    }

    @Redirect(method = "worldGenThreadsCanRun", at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/wrapperInterfaces/minecraft/IMinecraftSharedWrapper;isServerThreadHealthy()Z"))
    private static boolean auram$healthGate(IMinecraftSharedWrapper wrapper) {
        boolean healthy = wrapper.isServerThreadHealthy();
        if (!healthy) Dh333GenerationTrace.healthDeferred();
        return healthy;
    }

}
