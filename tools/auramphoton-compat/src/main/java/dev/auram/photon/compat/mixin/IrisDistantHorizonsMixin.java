package dev.auram.photon.compat.mixin;

import com.google.common.collect.ImmutableList;
import dev.auram.photon.compat.DistantHorizonsState;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.Map;

@Mixin(value = Iris.class, remap = false)
abstract class IrisDistantHorizonsMixin {
    @Shadow private static ShaderPack currentPack;
    @Shadow private static String currentPackName;

    @Unique private static WeakReference<ShaderPack> auramPhoton$loadedPack;
    @Unique private static Path auramPhoton$root;
    @Unique private static Map<String, String> auramPhoton$options;
    @Unique private static ImmutableList<StringPair> auramPhoton$defines;

    @Redirect(method = "loadExternalShaderpack", at = @At(
            value = "NEW", target = "net/irisshaders/iris/shaderpack/ShaderPack"))
    private static ShaderPack auramPhoton$rememberPack(
            Path root, Map<String, String> options, ImmutableList<StringPair> defines, String packName
    ) throws IOException {
        if (auramPhoton$isAuramPhoton(packName)) {
            defines = ImmutableList.<StringPair>builder()
                    .addAll(defines)
                    .add(new StringPair("AURAM_PHOTON_LPV_BACKEND", ""))
                    .build();
        }
        ShaderPack pack = new ShaderPack(root, options, defines);
        auramPhoton$loadedPack = new WeakReference<>(pack);
        auramPhoton$root = root;
        auramPhoton$options = Map.copyOf(options);
        auramPhoton$defines = defines;
        return pack;
    }

    @Unique
    private static boolean auramPhoton$isAuramPhoton(String name) {
        return "AuramPhoton".equals(name) || "AuramPhoton.zip".equals(name);
    }

    @Inject(method = "createPipeline", at = @At("HEAD"))
    private static void auramPhoton$refreshDistantHorizons(
            NamespacedId dimension, CallbackInfoReturnable<WorldRenderingPipeline> callback
    ) {
        if (currentPack == null || auramPhoton$loadedPack == null || currentPack != auramPhoton$loadedPack.get()
                || !auramPhoton$isAuramPhoton(currentPackName)) {
            return;
        }

        // DH can finish initialization after Oculus constructs the ShaderPack.
        // Reuse its open source path and saved options before the pipeline reads it.
        boolean renderingEnabled = DistantHorizonsState.renderingEnabled();
        boolean defined = auramPhoton$defines.stream().anyMatch(pair -> pair.key().equals("DISTANT_HORIZONS"));
        if (defined == renderingEnabled) {
            return;
        }

        ImmutableList.Builder<StringPair> defines = ImmutableList.builder();
        for (StringPair pair : auramPhoton$defines) {
            if (!pair.key().equals("DISTANT_HORIZONS")) {
                defines.add(pair);
            }
        }
        if (renderingEnabled) {
            defines.add(new StringPair("DISTANT_HORIZONS", ""));
        }

        ImmutableList<StringPair> environment = defines.build();
        try {
            ShaderPack rebuilt = new ShaderPack(auramPhoton$root, auramPhoton$options, environment);
            currentPack = rebuilt;
            auramPhoton$loadedPack = new WeakReference<>(rebuilt);
            auramPhoton$defines = environment;
            Iris.logger.info("Rebuilt AuramPhoton shader source with Distant Horizons rendering " + renderingEnabled);
        } catch (IOException | IllegalStateException e) {
            Iris.logger.error("Failed to refresh AuramPhoton after Distant Horizons changed state", e);
        }
    }
}
