package dev.auram.photon.compat.mixin;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.level.IDhServerLevel;
import net.irisshaders.iris.Iris;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.generation.queues.WorldGenerationQueue", remap = false)
abstract class Dh333WorldGenerationPriorityMixin {
    @Shadow public abstract int getWaitingTaskCount();
    @Shadow public abstract int getInProgressTaskCount();

    @Unique private MethodHandle auramPhoton$sampleChunkDemand;
    @Unique private MethodHandle auramPhoton$shouldDefer;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void auramPhoton$bindChunkDemand(IDhApiWorldGenerator generator, IDhServerLevel level, CallbackInfo callback) {
        ClassLoader loader = getClass().getClassLoader();
        Class<?> demand;
        try {
            demand = Class.forName("art.arcane.auram.util.ForegroundChunkDemand", false, loader);
        } catch (ClassNotFoundException absent) {
            return;
        }

        try {
            // Forge runtime names for ServerLevel#getServer, MinecraftServer#isDedicatedServer,
            // ServerLevel#getChunkSource and ServerChunkCache#chunkMap respectively.
            Object world = level.getServerLevelWrapper().getWrappedMcObject();
            Class<?> serverLevel = Class.forName("net.minecraft.server.level.ServerLevel", false, loader);
            if (!serverLevel.isInstance(world)) return;

            Object server = serverLevel.getMethod("m_7654_").invoke(world);
            if ((boolean) server.getClass().getMethod("m_6982_").invoke(server)) return;
            Object chunks = serverLevel.getMethod("m_7726_").invoke(world);
            Object map = chunks.getClass().getField("f_8325_").get(chunks);
            if (!demand.isInstance(map)) return;

            Class<?> policy = Class.forName("art.arcane.auram.util.DistantGenerationPriority", false, loader);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            auramPhoton$shouldDefer = lookup.findStatic(policy, "shouldDefer",
                    MethodType.methodType(boolean.class, boolean.class, int.class));
            auramPhoton$sampleChunkDemand = lookup.findVirtual(demand, "auram$samplePlayerChunkDemand",
                    MethodType.methodType(boolean.class)).bindTo(map);
        } catch (ReflectiveOperationException incompatible) {
            Iris.logger.warn("Could not bind AuramCore's chunk generation priority for Distant Horizons 3.3.3", incompatible);
        }
    }

    @Inject(method = "isGeneratorBusy", at = @At("RETURN"), cancellable = true)
    private void auramPhoton$prioritizePlayerChunks(CallbackInfoReturnable<Boolean> result) {
        if (auramPhoton$sampleChunkDemand == null || getWaitingTaskCount() == 0
                || Config.Common.WorldGenerator.chunkGeneratorMode.get() == EDhApiDistantGeneratorMode.INTERNAL_SERVER) {
            return;
        }

        try {
            // Request a new server-thread sample even when DH is already busy.
            boolean pending = (boolean) auramPhoton$sampleChunkDemand.invokeExact();
            if (!result.getReturnValueZ() && (boolean) auramPhoton$shouldDefer.invokeExact(pending, getInProgressTaskCount())) {
                result.setReturnValue(true);
            }
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Could not sample AuramCore's foreground chunk demand", failure);
        }
    }
}
