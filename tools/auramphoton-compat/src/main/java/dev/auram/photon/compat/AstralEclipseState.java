package dev.auram.photon.compat;

import net.irisshaders.iris.Iris;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;

public final class AstralEclipseState {
    private static final Bindings BINDINGS = Bindings.find();
    private static float solarProgress;
    private static float lunarCoverage;

    private AstralEclipseState() {}

    public static void clear() {
        solarProgress = 0.0f;
        lunarCoverage = 0.0f;
    }

    public static void sample(Object level, float partialTick) {
        if (BINDINGS == null) return;
        try {
            Optional<?> context = (Optional<?>) BINDINGS.context.invokeExact(level);
            if (context.isEmpty()) return;

            Object events = BINDINGS.events.invokeExact(context.get());
            float solar = BINDINGS.solar.progress(events, partialTick);
            float lunar = BINDINGS.lunar.progress(events, partialTick);
            solarProgress = solar;
            lunarCoverage = 1.0f - Math.abs(2.0f * lunar - 1.0f);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Could not read Astral Sorcery's eclipse calendar", failure);
        }
    }

    public static float solarProgress() {
        return solarProgress;
    }

    public static float lunarCoverage() {
        return lunarCoverage;
    }

    private record Event(MethodHandle get, MethodHandle active, MethodHandle tick, MethodHandle duration) {
        private static Event bind(Class<?> handler, Class<?> event, String getter)
                throws ReflectiveOperationException {
            return new Event(
                    bindVirtual(handler, getter, event, Object.class),
                    bindVirtual(event, "isActiveNow", boolean.class, boolean.class),
                    bindVirtual(event, "getEffectTick", float.class, float.class, float.class),
                    bindVirtual(event, "getEventDuration", int.class, int.class));
        }

        private float progress(Object events, float partialTick) throws Throwable {
            Object event = get.invokeExact(events);
            if (!(boolean) active.invokeExact(event)) return 0.0f;
            float progress = (float) tick.invokeExact(event, partialTick) / (int) duration.invokeExact(event);
            return Math.max(Float.MIN_NORMAL, Math.min(1.0f, progress));
        }
    }

    private record Bindings(MethodHandle context, MethodHandle events, Event solar, Event lunar) {
        private static Bindings find() {
            String prefix = "hellfirepvp.astralsorcery.common.constellation.level.";
            try {
                ClassLoader loader = AstralEclipseState.class.getClassLoader();
                Class<?> sky = Class.forName(prefix + "LevelSkyHandler", false, loader);
                Class<?> level = Class.forName("net.minecraft.world.level.Level", false, loader);
                Class<?> context = Class.forName(prefix + "LevelSkyContext", false, loader);
                Class<?> events = Class.forName(prefix + "CelestialEventHandler", false, loader);
                Class<?> solar = Class.forName(prefix + "event.SolarEclipseEvent", false, loader);
                Class<?> lunar = Class.forName(prefix + "event.LunarEclipseEvent", false, loader);
                MethodHandle getContext = MethodHandles.publicLookup()
                        .findStatic(sky, "getContext", MethodType.methodType(Optional.class, level))
                        .asType(MethodType.methodType(Optional.class, Object.class));
                return new Bindings(getContext, bindVirtual(context, "getCelestialEventHandler", events, Object.class),
                        Event.bind(events, solar, "getSolarEclipse"),
                        Event.bind(events, lunar, "getLunarEclipse"));
            } catch (ClassNotFoundException absent) {
                return null;
            } catch (ReflectiveOperationException incompatible) {
                Iris.logger.warn("Could not bind Astral Sorcery's eclipse calendar", incompatible);
                return null;
            }
        }
    }

    private static MethodHandle bindVirtual(Class<?> owner, String name, Class<?> result, Class<?> adaptedResult,
                                            Class<?>... arguments) throws ReflectiveOperationException {
        return MethodHandles.publicLookup().findVirtual(owner, name, MethodType.methodType(result, arguments))
                .asType(MethodType.methodType(adaptedResult, Object.class, arguments));
    }
}
