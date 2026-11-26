package dev.auram.bliss.qa;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.LongConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class RfFixture {
    private static ServerLevel routeLevel;
    private static List<LongConsumer> observers = List.of();

    private RfFixture() {}

    public static CompletableFuture<Void> configure(int count) {
        var server = Minecraft.m_91087_().m_91092_();
        return CompletableFuture.runAsync(() -> {
            var level = server.m_129880_(Level.f_46428_);
            var routes = new ArrayList<LongConsumer>(count);
            try {
                var observe = Class.forName("art.arcane.auram.rf.RfFlow")
                        .getMethod("observeLongRoute", ServerLevel.class, long[].class);
                for (int route = 0; route < count; route++) {
                    int y = 75 + route % 8;
                    int z = 34 + route / 8;
                    long[] positions = new long[16];
                    for (int i = 0; i <= 10; i++) positions[i] = BlockPos.m_121882_(33 + i, y, z);
                    for (int i = 1; i <= 5; i++) positions[10 + i] = BlockPos.m_121882_(43, y, z + i);
                    routes.add((LongConsumer) observe.invoke(null, level, positions));
                }
            } catch (ReflectiveOperationException error) {
                throw new CompletionException(error);
            }
            routeLevel = count == 0 ? null : level;
            observers = routes;
        }, server);
    }

    public static void tick(ServerLevel level) {
        if (level == routeLevel) {
            for (var observer : observers) observer.accept(100_000_000L);
        }
    }
}
