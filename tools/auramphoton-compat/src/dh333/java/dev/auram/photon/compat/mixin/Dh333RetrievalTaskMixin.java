package dev.auram.photon.compat.mixin;

import dev.auram.photon.compat.Dh333GenerationTrace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.generation.tasks.DataSourceRetrievalTask", remap = false)
abstract class Dh333RetrievalTaskMixin implements Dh333GenerationTrace.QueuedBatch {
    @Unique private final long auram$created = Dh333GenerationTrace.ENABLED ? System.nanoTime() : 0;

    @Override
    public long auram$queuedAt() {
        return auram$created;
    }
}
