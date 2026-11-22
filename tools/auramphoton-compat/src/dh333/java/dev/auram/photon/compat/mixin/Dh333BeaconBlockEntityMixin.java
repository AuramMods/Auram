package dev.auram.photon.compat.mixin;

import dev.auram.photon.compat.Dh333BeaconUpdates;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeaconBlock;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BeaconBlockEntity.BeaconBeamSection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.List;

@Mixin(value = BeaconBlockEntity.class, remap = false)
abstract class Dh333BeaconBlockEntityMixin {
    @Unique private List<BeaconBeamSection> auramPhoton$previousBeam = List.of();

    // BeaconBlockEntity.tick publishes its newly scanned beam before returning.
    @Inject(method = "m_155107_", at = @At("RETURN"))
    private static void auramPhoton$refreshChangedBeam(Level level, BlockPos pos, BlockState state,
                                                      BeaconBlockEntity beacon, CallbackInfo ci) {
        if (!(level instanceof ServerLevel server) || !ModList.get().isLoaded("distanthorizons")) {
            return;
        }

        var observer = (Dh333BeaconBlockEntityMixin) (Object) beacon;
        List<BeaconBeamSection> previous = observer.auramPhoton$previousBeam;
        List<BeaconBeamSection> beam = beacon.m_58702_();
        if (previous == beam) {
            return;
        }
        observer.auramPhoton$previousBeam = beam;
        if (!auramPhoton$sameBeam(previous, beam)) {
            LevelChunk chunk = Dh333BeaconUpdates.getLoadedChunk(server, pos);
            if (chunk != null) {
                Dh333BeaconUpdates.refresh(server, chunk);
            }
        }
    }

    @Inject(method = "m_7651_", at = @At("RETURN"))
    private void auramPhoton$refreshRemovedBeam(CallbackInfo ci) {
        var beacon = (BeaconBlockEntity) (Object) this;
        if (!(beacon.m_58904_() instanceof ServerLevel server) || !ModList.get().isLoaded("distanthorizons")) {
            return;
        }

        BlockPos pos = beacon.m_58899_();
        LevelChunk chunk = Dh333BeaconUpdates.getLoadedChunk(server, pos);
        // An unloading chunk can remove the block entity while keeping the beacon block.
        if (chunk != null && !(chunk.m_8055_(pos).m_60734_() instanceof BeaconBlock)) {
            Dh333BeaconUpdates.refresh(server, chunk);
        }
    }

    @Unique
    private static boolean auramPhoton$sameBeam(List<BeaconBeamSection> first, List<BeaconBeamSection> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int i = 0; i < first.size(); i++) {
            BeaconBeamSection a = first.get(i);
            BeaconBeamSection b = second.get(i);
            if (a.m_58723_() != b.m_58723_() || !Arrays.equals(a.m_58722_(), b.m_58722_())) {
                return false;
            }
        }
        return true;
    }
}
