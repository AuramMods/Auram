package dev.auram.photon.compat;

import com.seibel.distanthorizons.api.DhApi;
import net.minecraftforge.fml.ModList;

public final class DistantHorizonsState {
    private DistantHorizonsState() {
    }

    public static boolean renderingEnabled() {
        return ModList.get().isLoaded("distanthorizons")
                && DhApi.Delayed.configs != null
                && DhApi.Delayed.configs.graphics().renderingEnabled().getValue();
    }
}
