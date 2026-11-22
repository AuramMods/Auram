package dev.auram.photon.compat;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.pipeline.programs.ShaderMap;
import net.irisshaders.iris.shadows.ShadowRenderingState;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Supplier;

public final class AstralConstellationPass {
    private static Object skyShader;
    private static final MethodHandle GET_SHADER = bindShaderMap();
    private static final Supplier<Object> SKY_SHADER = () -> skyShader;

    private AstralConstellationPass() {}

    public static void enter() {
        String pack = Iris.getCurrentPackName();
        if ((!"AuramPhoton".equals(pack) && !"AuramPhoton.zip".equals(pack)
                && !"AuramBliss".equals(pack) && !"AuramBliss.zip".equals(pack))
                || ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            return;
        }

        if (Iris.getPipelineManager().getPipeline().orElse(null) instanceof IrisRenderingPipeline current) {
            try {
                skyShader = GET_SHADER.invokeExact(current.getShaderMap(), ShaderKey.SKY_TEXTURED_COLOR);
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Throwable failure) {
                throw new IllegalStateException("Could not select the constellation sky shader", failure);
            }
        }
    }

    public static void leave() {
        skyShader = null;
    }

    public static Supplier<?> shaderSupplier(Supplier<?> original) {
        return active() ? SKY_SHADER : original;
    }

    public static boolean active() {
        return skyShader != null;
    }

    public static int uniformValue() {
        return active() ? 1 : 0;
    }

    private static MethodHandle bindShaderMap() {
        try {
            Class<?> shader = Class.forName("net.minecraft.client.renderer.ShaderInstance");
            return MethodHandles.publicLookup()
                    .findVirtual(ShaderMap.class, "getShader", MethodType.methodType(shader, ShaderKey.class))
                    .asType(MethodType.methodType(Object.class, ShaderMap.class, ShaderKey.class));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not bind Oculus's sky shader map", failure);
        }
    }
}
