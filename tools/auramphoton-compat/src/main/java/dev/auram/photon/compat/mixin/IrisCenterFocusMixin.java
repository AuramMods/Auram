package dev.auram.photon.compat.mixin;

import com.google.common.collect.ImmutableSet;
import dev.auram.photon.compat.AutomaticFocusSampler;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.compat.dh.DHCompat;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.pathways.CenterDepthSampler;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Mixin(value = CenterDepthSampler.class, remap = false)
abstract class IrisCenterFocusMixin implements AutomaticFocusSampler {
    @Unique private boolean auramPhoton$automaticFocus;
    @Unique private boolean auramPhoton$firstFocus = true;
    @Unique private int auramPhoton$preHandDepth;
    @Unique private int auramPhoton$distantDepth;

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target =
            "Lnet/irisshaders/iris/gl/program/ProgramBuilder;begin(Ljava/lang/String;Ljava/lang/String;"
            + "Ljava/lang/String;Ljava/lang/String;Lcom/google/common/collect/ImmutableSet;)"
            + "Lnet/irisshaders/iris/gl/program/ProgramBuilder;"))
    private ProgramBuilder auramPhoton$createFocusProgram(String name, String vertex, String geometry,
                                                        String fragment, ImmutableSet<Integer> reserved) {
        String packName = Iris.getCurrentPackName();
        if (!"AuramPhoton".equals(packName) && !"AuramPhoton.zip".equals(packName)) {
            return ProgramBuilder.begin(name, vertex, geometry, fragment, reserved);
        }

        OptionValues options = Iris.getCurrentPack().orElseThrow().getShaderPackOptions().getOptionValues();
        if (!options.getBooleanValueOrDefault("DOF") || !"-1.0".equals(options.getStringValueOrDefault("DOF_FOCUS"))) {
            return ProgramBuilder.begin(name, vertex, geometry, fragment, reserved);
        }

        float scale = options.getBooleanValueOrDefault("TAA") && options.getBooleanValueOrDefault("TAAU")
                ? Float.parseFloat(options.getStringValueOrDefault("TAAU_RENDER_SCALE")) : 1.0f;
        ProgramBuilder builder = ProgramBuilder.begin(name, vertex, geometry, auramPhoton$focusSource(), reserved);
        builder.addDynamicSampler(() -> auramPhoton$preHandDepth, "auramPreHandDepth");
        builder.addDynamicSampler(() -> auramPhoton$distantDepth > 0
                ? auramPhoton$distantDepth : auramPhoton$preHandDepth, "auramDistantDepth");
        builder.uniform1f(UniformUpdateFrequency.ONCE, "auramFocusScale", () -> scale);
        builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "auramHasDistantDepth",
                () -> auramPhoton$distantDepth > 0 ? 1 : 0);
        builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "auramResetFocus", () -> auramPhoton$firstFocus ? 1 : 0);
        builder.uniformMatrix(UniformUpdateFrequency.PER_FRAME, "auramVanillaProjection",
                CapturedRenderingState.INSTANCE::getGbufferProjection);
        builder.uniformMatrix(UniformUpdateFrequency.PER_FRAME, "auramDistantProjection",
                () -> auramPhoton$distantDepth > 0
                        ? DHCompat.getProjection() : CapturedRenderingState.INSTANCE.getGbufferProjection());
        auramPhoton$automaticFocus = true;
        return builder;
    }

    @Override
    public boolean auramPhoton$usesAutomaticFocus() {
        return auramPhoton$automaticFocus;
    }

    @Override
    public void auramPhoton$sampleAutomaticFocus(int preHandDepth, int distantDepth) {
        auramPhoton$preHandDepth = preHandDepth;
        auramPhoton$distantDepth = distantDepth;
        ((CenterDepthSampler)(Object)this).sampleCenterDepth();
        auramPhoton$firstFocus = false;
    }

    @Unique
    private static String auramPhoton$focusSource() {
        try (InputStream source = AutomaticFocusSampler.class.getResourceAsStream("focus/center.fsh")) {
            if (source == null) throw new IllegalStateException("AuramPhoton automatic focus shader is missing");
            return new String(source.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load AuramPhoton automatic focus shader", e);
        }
    }
}
