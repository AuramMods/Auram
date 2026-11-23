package dev.auram.photon.compat.mixin;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.generation.tasks.DataSourceRetrievalTask;
import com.seibel.distanthorizons.core.level.IDhServerLevel;
import dev.auram.photon.compat.Dh333GenerationTrace;
import net.irisshaders.iris.Iris;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
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
import java.util.concurrent.CompletableFuture;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.generation.queues.WorldGenerationQueue", remap = false)
abstract class Dh333WorldGenerationPriorityMixin {
    @Unique private static final int AURAM_OVERWORLD_BATCH_LIMIT =
            Math.max(0, Integer.getInteger("auram.dh.overworldBatches", 0));

    @Shadow public abstract int getWaitingTaskCount();
    @Shadow public abstract int getInProgressTaskCount();

    @Unique private MethodHandle auramPhoton$sampleChunkDemand;
    @Unique private MethodHandle auramPhoton$shouldDefer;
    @Unique private Dh333GenerationTrace auram$trace;
    @Unique private boolean auram$overworld;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void auramPhoton$bindChunkDemand(IDhApiWorldGenerator generator, IDhServerLevel level, CallbackInfo callback) {
        Object world = level.getServerLevelWrapper().getWrappedMcObject();
        if (!(world instanceof ServerLevel serverLevel)) return;
        auram$overworld = serverLevel.m_46472_() == Level.f_46428_;
        auram$trace = Dh333GenerationTrace.open(serverLevel);

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
            Object server = ServerLevel.class.getMethod("m_7654_").invoke(world);
            if ((boolean) server.getClass().getMethod("m_6982_").invoke(server)) return;
            Object chunks = ServerLevel.class.getMethod("m_7726_").invoke(world);
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
        if (getWaitingTaskCount() == 0) return;
        boolean internal = Config.Common.WorldGenerator.chunkGeneratorMode.get() == EDhApiDistantGeneratorMode.INTERNAL_SERVER;
        boolean limitInternal = internal && auram$overworld && AURAM_OVERWORLD_BATCH_LIMIT > 0;
        if (auram$trace != null && internal) {
            auram$trace.queueGate(result.getReturnValueZ() ? "dh_capacity" : "ready");
        }
        if (limitInternal && getInProgressTaskCount() >= AURAM_OVERWORLD_BATCH_LIMIT) {
            if (auram$trace != null) auram$trace.queueGate("overworld_capacity");
            result.setReturnValue(true);
        }
        if (auramPhoton$sampleChunkDemand == null || (internal && !limitInternal)) return;

        try {
            // Request a new server-thread sample even when DH is already busy.
            boolean pending = (boolean) auramPhoton$sampleChunkDemand.invokeExact();
            if (!result.getReturnValueZ() && (boolean) auramPhoton$shouldDefer.invokeExact(pending, getInProgressTaskCount())) {
                if (auram$trace != null && internal) auram$trace.queueGate("player_chunks");
                result.setReturnValue(true);
            }
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Could not sample AuramCore's foreground chunk demand", failure);
        }
    }

    @Inject(method = "tryQueueNewWorldGenRequestsAsync", at = @At("RETURN"))
    private void auram$reportGeneration(CallbackInfo callback) {
        if (auram$trace != null && Config.Common.WorldGenerator.chunkGeneratorMode.get() == EDhApiDistantGeneratorMode.INTERNAL_SERVER) {
            auram$trace.report(getWaitingTaskCount(), getInProgressTaskCount());
        }
    }

    @Inject(method = "startGenerationEvent", at = @At("RETURN"))
    private void auram$recordLodCompletion(DataSourceRetrievalTask task,
            CallbackInfoReturnable<CompletableFuture<FullDataSourceV2>> result) {
        if (auram$trace == null || Config.Common.WorldGenerator.chunkGeneratorMode.get() != EDhApiDistantGeneratorMode.INTERNAL_SERVER) return;
        long submit = System.nanoTime();
        long queued = ((Dh333GenerationTrace.QueuedBatch) (Object) task).auram$queuedAt();
        auram$trace.batchStart(queued, submit);
        result.getReturnValue().whenComplete((lod, error) -> {
            if (error == null && lod != null) {
                auram$trace.batchComplete(submit, task.widthInChunks * task.widthInChunks);
            }
        });
    }

    @Inject(method = "close", at = @At("RETURN"))
    private void auram$closeTrace(CallbackInfo callback) {
        if (auram$trace != null) auram$trace.close();
    }
}
