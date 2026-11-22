package dev.auram.photon.compat;

import com.seibel.distanthorizons.api.DhApi;
import net.irisshaders.iris.Iris;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class WarpMistState {
    private static final MethodHandle VISIBLE_INTENSITY = findVisibleIntensity();

    private WarpMistState() {}

    public static boolean available() {
        return VISIBLE_INTENSITY != null;
    }

    public static float intensity() {
        float intensity = visibleIntensity();
        // Oculus disables DH's native fog draw through the API while its shader pipeline owns the scene.
        if (intensity <= 0.0f || !DistantHorizonsState.renderingEnabled()
                || !DhApi.Delayed.configs.graphics().fog().enableDhFog().getTrueValue()) {
            return 0.0f;
        }
        return intensity;
    }

    public static float visibleIntensity() {
        if (!available()) return 0.0f;
        try {
            return (float) VISIBLE_INTENSITY.invokeExact();
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Could not read Thaumaturge Warp Mist", failure);
        }
    }

    private static MethodHandle findVisibleIntensity() {
        try {
            Class<?> state = Class.forName("com.leclowndu93150.thaumaturge.client.warp.WarpFogState",
                    false, WarpMistState.class.getClassLoader());
            return MethodHandles.publicLookup().findStatic(state, "visibleIntensity", MethodType.methodType(float.class));
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException incompatible) {
            Iris.logger.warn("Could not bind Thaumaturge Warp Mist", incompatible);
            return null;
        }
    }
}
