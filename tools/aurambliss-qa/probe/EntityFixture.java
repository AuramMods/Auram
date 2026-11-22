package dev.auram.bliss.test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.opengl.GL41C.*;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
abstract class EntityFixture {
    @Inject(method = "finalizeLevelRendering", at = @At("TAIL"))
    private void applyFixture(CallbackInfo callback) throws Exception {
        if (Files.deleteIfExists(Path.of("bliss-fixture-entities"))) {
            Class<?> clientType = Class.forName("net.minecraft.client.Minecraft");
            Object client = clientType.getMethod("m_91087_").invoke(null);
            Object server = clientType.getMethod("m_91092_").invoke(client);
            ((Executor) server).execute(() -> {
                try {
                    Object commands = server.getClass().getMethod("m_129892_").invoke(server);
                    Object source = server.getClass().getMethod("m_129893_").invoke(server);
                    var command = commands.getClass().getMethod("m_230957_", source.getClass(), String.class);
                    for (String statement : new String[] {
                            "difficulty normal",
                            "execute at @p anchored eyes run summon minecraft:blaze ^0 ^0 ^3 {NoAI:1b,NoGravity:1b,Silent:1b}",
                            "execute at @p anchored eyes run summon minecraft:item ^-1 ^0 ^3 {Item:{id:\"minecraft:sea_lantern\",Count:1b},PickupDelay:32767s,Age:-32768s,NoGravity:1b}",
                            "execute at @p anchored eyes run setblock ^1 ^0 ^4 minecraft:beacon"
                    }) {
                        int result = (int) command.invoke(commands, source, statement);
                        if (result != 1 && !statement.startsWith("difficulty")) throw new IllegalStateException(statement + " returned " + result);
                    }
                    System.out.println("Bliss fixture entities ready");
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            });
        }
        if (!Files.deleteIfExists(Path.of("bliss-read-entities"))) return;
        Class<?> lightVolume = Class.forName("dev.auram.photon.compat.lpv.RasterLightVolume");
        Field active = lightVolume.getDeclaredField("active");
        active.setAccessible(true);
        Object volume = active.get(null);
        Field occupancy = lightVolume.getDeclaredField("occupancy");
        occupancy.setAccessible(true);
        Object target = occupancy.get(volume);
        var getter = target.getClass().getDeclaredMethod("texture");
        getter.setAccessible(true);
        int texture = (int) getter.invoke(target);
        int original = glGetInteger(GL_TEXTURE_BINDING_2D);
        glBindTexture(GL_TEXTURE_2D, texture);
        int width = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
        int height = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
        short[] ids = new short[width * height];
        glGetTexImage(GL_TEXTURE_2D, 0, GL_RED_INTEGER, GL_UNSIGNED_SHORT, ids);
        glBindTexture(GL_TEXTURE_2D, original);
        int blaze = 0, lantern = 0, beacon = 0, occupied = 0;
        var highIds = new java.util.TreeMap<Integer, Integer>();
        for (short id : ids) {
            if (id != 0) occupied++;
            if (Short.toUnsignedInt(id) >= 1000) highIds.merge(Short.toUnsignedInt(id), 1, Integer::sum);
            if (id == 1603) blaze++;
            if (id == 1020 || id == 242) lantern++;
            if (id == 104) beacon++;
        }
        System.out.println("Bliss high voxel IDs " + highIds);
        System.out.printf("QA_EVT {\"event\":\"entity_voxels\",\"status\":\"info\",\"context\":{\"blaze\":%d,\"droppedLantern\":%d,\"beacon\":%d,\"occupied\":%d,\"gl\":%d}}%n",
                blaze, lantern, beacon, occupied, glGetError());
    }
}
