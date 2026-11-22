package dev.auram.bliss.test;

import dev.auram.bliss.qa.SceneChecks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.GameRenderer", remap = false)
abstract class LifecycleProbe {
    @Inject(method = "m_109093_(FJZ)V", at = @At("TAIL"))
    private void runLifecycleCommand(CallbackInfo callback) {
        Path path = Path.of("bliss-lifecycle-command");
        if (!Files.isRegularFile(path)) return;
        String command = "";
        long start = System.nanoTime();
        try {
            command = Files.readString(path).trim();
            Files.delete(path);
            String[] parts = command.split(" ", 2);
            String action = parts[0];
            String argument = parts.length == 2 ? parts[1] : "";
            switch (action) {
                case "shader" -> {
                    Iris.getIrisConfig().setShadersEnabled(true);
                    Iris.getIrisConfig().setShaderPackName(argument);
                    Iris.getIrisConfig().save();
                    Iris.reload();
                }
                case "shader-reload" -> Iris.reload();
                case "capture" -> SceneChecks.capture(argument);
                case "fullscreen" -> SceneChecks.fullscreen(argument);
                case "enter", "leave" -> SceneChecks.world(action);
                case "server" -> {
                    String requested = command;
                    SceneChecks.command(argument).whenComplete((ignored, error) -> completeLifecycle(requested, start, error));
                    return;
                }
                case "resize" -> {
                    String[] size = argument.split("x");
                    GLFW.glfwSetWindowSize(GLFW.glfwGetCurrentContext(),
                            Integer.parseInt(size[0]), Integer.parseInt(size[1]));
                }
                case "resources" -> {
                    Object client = getMinecraft();
                    CompletableFuture<?> reload = (CompletableFuture<?>) client.getClass().getMethod("m_91391_").invoke(client);
                    reload.whenComplete((ignored, error) -> completeLifecycle("resources", start, error));
                    return;
                }
                case "gui-scale" -> {
                    Object client = getMinecraft();
                    Object options = client.getClass().getField("f_91066_").get(client);
                    Object guiScale = options.getClass().getMethod("m_231928_").invoke(options);
                    guiScale.getClass().getMethod("m_231514_", Object.class).invoke(guiScale, Integer.parseInt(argument));
                    client.getClass().getMethod("m_5741_").invoke(client);
                }
                case "dh" -> {
                    Class<?> delayed = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
                    var config = delayed.getField("configs");
                    var graphics = config.getType().getMethod("graphics");
                    var enabled = graphics.getReturnType().getMethod("renderingEnabled");
                    Object value = enabled.invoke(graphics.invoke(config.get(null)));
                    boolean requested = Boolean.parseBoolean(argument);
                    boolean accepted = (boolean) enabled.getReturnType().getMethod("setValue", Object.class)
                            .invoke(value, requested);
                    if (!accepted) throw new IllegalStateException("DH declined the renderer toggle");
                }
                case "snapshot" -> {
                    long window = GLFW.glfwGetCurrentContext();
                    int[] width = new int[1], height = new int[1];
                    GLFW.glfwGetFramebufferSize(window, width, height);
                    var renderPipeline = Iris.getPipelineManager().getPipelineNullable();
                    int depth = renderPipeline instanceof IrisRenderingPipeline pipeline ? pipeline.getDHCompat().getDepthTex() : -1;
                    int light = 0;
                    try {
                        var volume = Class.forName("dev.auram.photon.compat.lpv.RasterLightVolume");
                        light = (int) volume.getMethod("activeBlissTexture").invoke(null);
                    } catch (ClassNotFoundException ignored) {
                        // The baseline replay can be run without the optional companion.
                    }
                    System.out.printf("QA_STATE pack=%s pipeline=%d lod=%d light=%d size=%dx%d dimension=%s fullscreen=%d visible=%d focused=%d gl=%d%n",
                            Iris.getCurrentPackName(), System.identityHashCode(renderPipeline), depth, light, width[0], height[0],
                            SceneChecks.dimension(), GLFW.glfwGetWindowMonitor(window) == 0 ? 0 : 1,
                            GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE), GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED),
                            GL11C.glGetError());
                }
                default -> throw new IllegalArgumentException("Unknown lifecycle action " + action);
            }
            completeLifecycle(command, start, null);
        } catch (Exception error) {
            completeLifecycle(command, start, error);
        }
    }

    private static Object getMinecraft() throws Exception {
        return Class.forName("net.minecraft.client.Minecraft").getMethod("m_91087_").invoke(null);
    }

    private static void completeLifecycle(String command, long start, Throwable error) {
        if (error == null) {
            System.out.printf(java.util.Locale.ROOT, "QA_COMMAND %s | %.3f ms%n", command, (System.nanoTime() - start) / 1e6);
        } else {
            System.out.println("QA_COMMAND_ERROR " + command);
            error.printStackTrace(System.out);
        }
    }
}
