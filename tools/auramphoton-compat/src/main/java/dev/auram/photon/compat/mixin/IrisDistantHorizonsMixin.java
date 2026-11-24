package dev.auram.photon.compat.mixin;

import com.google.common.collect.ImmutableList;
import dev.auram.photon.compat.DistantHorizonsState;
import dev.auram.photon.compat.WarpMistState;
import dev.auram.photon.compat.AstralEclipseState;
import dev.auram.photon.compat.lpv.MetalLightVolume;
import dev.auram.photon.compat.lpv.RasterLightVolume;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20C;
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
            ImmutableList.Builder<StringPair> auramDefines = ImmutableList.<StringPair>builder()
                    .addAll(defines)
                    .add(new StringPair("AURAM_PHOTON_LPV_BACKEND", ""))
                    .add(new StringPair("AURAM_PHOTON_EXPOSURE_METER", ""));
            if (WarpMistState.available()) {
                auramDefines.add(new StringPair("AURAM_PHOTON_DH_WARP_MIST", ""));
            }
            defines = auramDefines.build();
        } else if (auramPhoton$isAuramBliss(packName)) {
            ImmutableList.Builder<StringPair> auramDefines = ImmutableList.<StringPair>builder()
                    .addAll(defines)
                    .add(new StringPair("AURAM_BLISS_LPV_BACKEND", ""))
                    .add(new StringPair("AURAM_BLISS_RF_LPV", ""));
            if (WarpMistState.available()) auramDefines.add(new StringPair("AURAM_BLISS_WARP_MIST", ""));
            if (AstralEclipseState.available()) auramDefines.add(new StringPair("AURAM_BLISS_ASTRAL_SKY", ""));
            if (GL.getCapabilities().glDispatchCompute == 0 || GL.getCapabilities().glClearTexImage == 0
                    || GL20C.glGetInteger(GL20C.GL_MAX_TEXTURE_IMAGE_UNITS) < 32) {
                auramDefines.add(new StringPair("AURAM_BLISS_AUTO_COMPAT", ""));
            }
            defines = auramDefines.build();
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

    @Unique
    private static boolean auramPhoton$isAuramBliss(String name) {
        return "AuramBliss".equals(name) || "AuramBliss.zip".equals(name);
    }

    @Unique
    private static boolean auramPhoton$hasLoadedPack() {
        return currentPack != null && auramPhoton$loadedPack != null && currentPack == auramPhoton$loadedPack.get()
                && (auramPhoton$isAuramPhoton(currentPackName) || auramPhoton$isAuramBliss(currentPackName));
    }

    @Inject(method = "createPipeline", at = @At("HEAD"))
    private static void auramPhoton$refreshDistantHorizons(
            NamespacedId dimension, CallbackInfoReturnable<WorldRenderingPipeline> callback
    ) {
        if (!auramPhoton$hasLoadedPack()) return;

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
            Iris.logger.info("Rebuilt " + currentPackName + " shader source with Distant Horizons rendering " + renderingEnabled);
        } catch (IOException | IllegalStateException e) {
            Iris.logger.error("Failed to refresh " + currentPackName + " after Distant Horizons changed state", e);
        }
    }

    @Redirect(method = "createPipeline", at = @At(
            value = "NEW", target = "net/irisshaders/iris/pipeline/IrisRenderingPipeline"))
    private static IrisRenderingPipeline auramPhoton$chooseLightVolume(ProgramSet programs, NamespacedId dimension) {
        if (!MetalLightVolume.requested() || !auramPhoton$isAuramPhoton(currentPackName)
                || !auramPhoton$hasLoadedPack() || !RasterLightVolume.usesRasterVolume(programs)) {
            return new IrisRenderingPipeline(programs);
        }

        ProgramSet metalPrograms;
        try {
            ImmutableList<StringPair> environment = ImmutableList.<StringPair>builder()
                    .addAll(auramPhoton$defines)
                    .add(new StringPair("AURAM_PHOTON_METAL_LPV", ""))
                    .build();
            metalPrograms = new ShaderPack(auramPhoton$root, auramPhoton$options, environment).getProgramSet(dimension);
        } catch (IOException | RuntimeException error) {
            Iris.logger.warn("AuramPhoton Metal shader preparation failed. Using OpenGL colored lighting. {}", error.toString());
            return new IrisRenderingPipeline(programs);
        }
        return RasterLightVolume.createMetalPipeline(programs, metalPrograms);
    }
}
