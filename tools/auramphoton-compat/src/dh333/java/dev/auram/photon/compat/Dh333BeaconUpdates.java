package dev.auram.photon.compat;

import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper_forge;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper_forge;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.config.eventHandlers.IgnoredDimensionCsvHandler;
import com.seibel.distanthorizons.core.world.DhApiWorldProxy;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;

public final class Dh333BeaconUpdates {
    private Dh333BeaconUpdates() {}

    public static LevelChunk getLoadedChunk(ServerLevel server, BlockPos pos) {
        return server.m_7726_().m_7131_(pos.m_123341_() >> 4, pos.m_123343_() >> 4);
    }

    public static void refresh(ServerLevel server, LevelChunk chunk) {
        var world = SharedApi.tryGetDhServerWorld();
        if (world == null || DhApiWorldProxy.INSTANCE.tryGetReadOnly()) {
            return;
        }

        var wrapper = ServerLevelWrapper_forge.getWrapper(server);
        var level = world.getLevel(wrapper);
        if (level == null
                || IgnoredDimensionCsvHandler.INSTANCE.dimensionNameShouldBeIgnored(wrapper.getDimensionName())) {
            return;
        }

        var dhChunk = new ChunkWrapper_forge(chunk, wrapper);
        var pos = dhChunk.getChunkPos();
        var neighbors = new ArrayList<IChunkWrapper>(8);
        var cache = server.m_7726_();
        // DH checks the beacon's 3×3 base, which can cross a chunk boundary.
        for (int z = -1; z <= 1; z++) {
            for (int x = -1; x <= 1; x++) {
                if (x == 0 && z == 0) {
                    continue;
                }

                LevelChunk neighbor = cache.m_7131_(pos.getX() + x, pos.getZ() + z);
                if (neighbor != null) {
                    neighbors.add(new ChunkWrapper_forge(neighbor, wrapper));
                }
            }
        }

        // Use DH's collector and delayed save even when its terrain hash skips the edit.
        level.updateBeaconBeamsForChunk(dhChunk, neighbors);
    }
}
