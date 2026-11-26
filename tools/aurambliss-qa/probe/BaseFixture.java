package dev.auram.bliss.qa;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class BaseFixture {
    private BaseFixture() {}

    public static CompletableFuture<Void> refreshChests() {
        var server = Minecraft.m_91087_().m_91092_();
        return CompletableFuture.runAsync(() -> {
            try {
                scan(server.m_129880_(Level.f_46428_), true);
            } catch (ReflectiveOperationException error) {
                throw new CompletionException(error);
            }
        }, server);
    }

    public static void verifyChests(String expected) throws ReflectiveOperationException {
        var actual = scan(Minecraft.m_91087_().f_91073_, false);
        if (!actual.equals(expected)) {
            throw new IllegalStateException("Expected crystal/loaded/opaque counts " + expected + ", found " + actual);
        }
        System.out.println("QA_BASE_CHESTS crystal/loaded/opaque=" + actual);
    }

    public static void verifyChestTop(String input) throws ReflectiveOperationException {
        String[] values = input.split(" ", 4);
        var pos = new BlockPos(Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2]));
        var chest = Minecraft.m_91087_().f_91073_.m_7702_(pos);
        var stacks = (List<?>) chest.getClass().getMethod("getTopItems").invoke(chest);
        var item = ((ItemStack) stacks.get(0)).m_41720_();
        var actual = BuiltInRegistries.f_257033_.m_7981_(item).toString();
        if (!actual.equals(values[3])) throw new IllegalStateException("Unexpected chest item at " + pos + ": " + actual);
        System.out.println("QA_BASE_INVENTORY " + pos + " " + actual);
    }

    private static String scan(Level level, boolean refresh) throws ReflectiveOperationException {
        var crystalClass = Class.forName("com.progwml6.ironchest.common.block.entity.ICrystalChest");
        var chestClass = Class.forName("com.progwml6.ironchest.common.block.regular.entity.AbstractIronChestBlockEntity");
        Method stacks = crystalClass.getMethod("getTopItems");
        Method sort = crystalClass.getMethod("sortTopStacks");
        int crystal = 0, loaded = 0, opaque = 0;
        for (int y = 74; y <= 89; y++) {
            for (int x = 32; x <= 63; x++) {
                var blockEntity = level.m_7702_(new BlockPos(x, y, 40));
                if (crystalClass.isInstance(blockEntity)) {
                    crystal++;
                    if (refresh) {
                        sort.invoke(blockEntity);
                    } else {
                        var items = (List<?>) stacks.invoke(blockEntity);
                        if (items.stream().anyMatch(item -> !((ItemStack) item).m_41619_())) loaded++;
                    }
                } else if (chestClass.isInstance(blockEntity)) {
                    opaque++;
                }
            }
        }
        return crystal + "," + loaded + "," + opaque;
    }
}
