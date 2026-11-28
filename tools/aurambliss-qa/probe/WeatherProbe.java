package dev.auram.bliss.qa;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.concurrent.CompletableFuture;

public final class WeatherProbe {
    private static volatile long sleepStarted = -1;
    private WeatherProbe() {}

    public static CompletableFuture<Void> beginSleep() {
        var server = Minecraft.m_91087_().m_91092_();
        return CompletableFuture.runAsync(() -> {
            var player = server.m_6846_().m_11314_().get(0);
            BlockPos bed = BlockPos.m_274561_(player.m_20185_() + 2.0, player.m_20186_(), player.m_20189_());
            sleepStarted = player.m_284548_().m_46468_();
            var result = player.m_7720_(bed);
            if (result.left().isPresent()) {
                throw new IllegalStateException("Player could not enter the fixture bed: " + result.left().get());
            }
        }, server);
    }

    public static void capture(String label) throws ReflectiveOperationException {
        var client = Minecraft.m_91087_();
        String weather = client.m_91092_() == null ? "remote" : debugLine(client);

        Class<?> clientWeather = Class.forName("art.arcane.auram.weather.client.WeatherClient");
        var entity = client.m_91288_();
        float precipitation = (float) clientWeather.getMethod("precipitation", double.class, double.class)
                .invoke(null, entity.m_20185_(), entity.m_20189_());
        Class<?> texture = Class.forName("art.arcane.auram.weather.client.WeatherTexture");
        var active = texture.getDeclaredField("active");
        active.setAccessible(true);
        if (label.equals("dry") && precipitation > 0.05f) {
            throw new IllegalStateException("Dry fixture sampled precipitation " + precipitation);
        }
        if ((label.equals("east") || label.equals("west") || label.equals("clear") || label.equals("roof")
                || label.equals("guest"))
                && precipitation < 0.55f) {
            throw new IllegalStateException("Forced rain sampled precipitation " + precipitation);
        }
        if (label.equals("clear") == active.getBoolean(null)) {
            throw new IllegalStateException("Unexpected shader override state for " + label);
        }
        if (label.equals("after-sleep")) {
            long serverTime = client.m_91092_().m_129783_().m_46468_();
            if (sleepStarted < 0 || serverTime - sleepStarted < 4000 || precipitation < 0.55f) {
                throw new IllegalStateException("Expected sleep to advance server day time and preserve the storm. "
                        + "Before " + sleepStarted + ", after " + serverTime + ", precipitation " + precipitation);
            }
            System.out.println("QA_WEATHER_SLEEP " + sleepStarted + " -> " + serverTime);
        }
        System.out.println("QA_WEATHER " + label + " precip=" + precipitation + " " + weather);
    }

    private static String debugLine(Minecraft client) throws ReflectiveOperationException {
        var overlayField = net.minecraft.client.gui.Gui.class.getDeclaredField("f_92995_");
        overlayField.setAccessible(true);
        Object overlay = overlayField.get(client.f_91065_);
        var method = overlay.getClass().getDeclaredMethod("m_94075_");
        method.setAccessible(true);
        var lines = (java.util.List<?>) method.invoke(overlay);
        return lines.stream().map(Object::toString).filter(line -> line.startsWith("Weather "))
                .findFirst().orElseThrow(() -> new IllegalStateException("F3 has no weather line"));
    }

}
