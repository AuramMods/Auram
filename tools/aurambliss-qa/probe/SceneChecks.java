package dev.auram.bliss.qa;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import javax.imageio.ImageIO;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL41C.*;

public final class SceneChecks {
    private static final int[] PACK_FIELDS = {
        GL_PACK_ALIGNMENT, GL_PACK_ROW_LENGTH, GL_PACK_SKIP_ROWS, GL_PACK_SKIP_PIXELS
    };

    private SceneChecks() {}

    private static Object client() throws ReflectiveOperationException {
        return Class.forName("net.minecraft.client.Minecraft").getMethod("m_91087_").invoke(null);
    }

    public static String dimension() throws ReflectiveOperationException {
        var client = client();
        var level = client.getClass().getField("f_91073_").get(client);
        if (level == null) return "none";
        var key = level.getClass().getMethod("m_46472_").invoke(level);
        return key.getClass().getMethod("m_135782_").invoke(key).toString();
    }

    public static CompletableFuture<Void> command(String statement) throws ReflectiveOperationException {
        var client = client();
        var server = client.getClass().getMethod("m_91092_").invoke(client);
        return CompletableFuture.runAsync(() -> {
            try {
                var commands = server.getClass().getMethod("m_129892_").invoke(server);
                var source = server.getClass().getMethod("m_129893_").invoke(server);
                int result = (int) commands.getClass().getMethod("m_230957_", source.getClass(), String.class)
                        .invoke(commands, source, statement);
                if (result == 0 && statement.contains("tp @p")) throw new IllegalStateException("Teleport did not run: " + statement);
            } catch (ReflectiveOperationException error) {
                throw new CompletionException(error);
            }
        }, (Executor) server);
    }

    public static void world(String action) throws ReflectiveOperationException {
        var client = client();
        var screen = Class.forName("net.minecraft.client.gui.screens.Screen");
        var title = Class.forName("net.minecraft.client.gui.screens.TitleScreen").getConstructor().newInstance();
        if (action.equals("enter")) {
            var opener = client.getClass().getMethod("m_231466_").invoke(client);
            opener.getClass().getMethod("m_233133_", screen, String.class).invoke(opener, title, "ShaderLab");
        } else {
            var level = client.getClass().getField("f_91073_").get(client);
            level.getClass().getMethod("m_7462_").invoke(level);
            client.getClass().getMethod("m_91320_", screen).invoke(client, title);
            client.getClass().getMethod("m_91152_", screen).invoke(client, title);
        }
    }

    public static void fullscreen(String display) throws ReflectiveOperationException {
        var client = client();
        var window = client.getClass().getMethod("m_91268_").invoke(client);
        var type = window.getClass();
        if (!display.equals("off")) {
            var monitors = GLFW.glfwGetMonitors();
            long selected = 0;
            for (int i = 0; monitors != null && i < monitors.limit(); i++) {
                long monitor = monitors.get(i);
                if (display.equals(GLFW.glfwGetMonitorName(monitor))) selected = monitor;
            }
            if (selected == 0) throw new IllegalStateException("QA display unavailable: " + display);
            var mode = GLFW.glfwGetVideoMode(selected);
            int[] x = new int[1], y = new int[1];
            GLFW.glfwGetMonitorPos(selected, x, y);
            GLFW.glfwSetWindowPos(GLFW.glfwGetCurrentContext(), x[0], y[0]);
            var videoMode = Class.forName("com.mojang.blaze3d.platform.VideoMode");
            type.getMethod("m_85405_", Optional.class).invoke(window,
                    Optional.of(videoMode.getConstructor(GLFWVidMode.class).newInstance(mode)));
        }
        type.getMethod("m_85438_").invoke(window);
    }

    public static void capture(String label) throws Exception {
        if (!label.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Invalid capture name: " + label);
        var client = client();
        var target = client.getClass().getMethod("m_91385_").invoke(client);
        var type = target.getClass();
        int framebuffer = type.getField("f_83920_").getInt(target);
        int width = type.getField("f_83915_").getInt(target);
        int height = type.getField("f_83916_").getInt(target);
        int previousFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int previousBuffer = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        int[] pack = new int[PACK_FIELDS.length];
        for (int i = 0; i < pack.length; i++) {
            pack[i] = glGetInteger(PACK_FIELDS[i]);
            glPixelStorei(PACK_FIELDS[i], i == 0 ? 1 : 0);
        }
        var pixels = MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(width, height), 4));
        try {
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            int[] row = new int[width];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int index = (y * width + x) * 4;
                    row[x] = (Byte.toUnsignedInt(pixels.get(index)) << 16)
                            | (Byte.toUnsignedInt(pixels.get(index + 1)) << 8)
                            | Byte.toUnsignedInt(pixels.get(index + 2));
                }
                image.setRGB(0, height - 1 - y, width, 1, row, 0, width);
            }
            var directory = Path.of("bliss-frames");
            Files.createDirectories(directory);
            if (!ImageIO.write(image, "png", directory.resolve(label + ".png").toFile())) {
                throw new IllegalStateException("PNG writer unavailable");
            }
        } finally {
            MemoryUtil.memFree(pixels);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousFramebuffer);
            glBindBuffer(GL_PIXEL_PACK_BUFFER, previousBuffer);
            for (int i = 0; i < pack.length; i++) glPixelStorei(PACK_FIELDS[i], pack[i]);
        }
    }
}
