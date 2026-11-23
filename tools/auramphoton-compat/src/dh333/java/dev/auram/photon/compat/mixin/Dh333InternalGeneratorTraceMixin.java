package dev.auram.photon.compat.mixin;

import com.seibel.distanthorizons.common.wrappers.worldGeneration.params.GlobalWorldGenParams_forge;
import dev.auram.photon.compat.Dh333GenerationTrace;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.worldGeneration.DhInternalServerGenerator_forge", remap = false)
abstract class Dh333InternalGeneratorTraceMixin {
    @Shadow @Final private GlobalWorldGenParams_forge params;

    @Redirect(method = "requestChunkFromServerAsync", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<CompletableFuture<ChunkAccess>> auram$traceFull(
            Supplier<CompletableFuture<ChunkAccess>> submit, Executor executor, ChunkPos pos) {
        Dh333GenerationTrace trace = Dh333GenerationTrace.get(params.mcServerLevel);
        if (trace == null) return CompletableFuture.supplyAsync(submit, executor);
        long key = pos.m_45588_();
        Dh333GenerationTrace.Request request = trace.begin(key);
        try {
            var task = CompletableFuture.supplyAsync(submit, executor);
            task.whenComplete((inner, failure) -> {
                if (failure != null) trace.end(key, request, false);
                else inner.whenComplete((chunk, error) -> trace.end(key, request, error == null && chunk != null));
            });
            return task;
        } catch (RuntimeException | Error failure) {
            trace.end(key, request, false);
            throw failure;
        }
    }
}
