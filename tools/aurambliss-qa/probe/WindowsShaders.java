package dev.auram.bliss.qa;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTests;
import net.irisshaders.iris.gl.shader.StandardMacros;
import net.irisshaders.iris.gl.state.ShaderAttributeInputs;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.shaderpack.DimensionId;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.programs.ComputeSource;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.texture.TextureStage;

public final class WindowsShaders {
    private static boolean auditing;

    private WindowsShaders() {}

    public static boolean isAuditing() {
        return auditing;
    }

    public static boolean isHostDefine(String key) {
        return key.startsWith("MC_OS_") || key.startsWith("MC_GL_") || key.startsWith("IRIS_FEATURE_")
                || key.equals("MC_GLSL_VERSION") || key.equals("DISTANT_HORIZONS");
    }

    public static void export(Path work) throws IOException {
        for (String line : Files.readAllLines(work.resolve("cases.csv"))) {
            String[] fields = line.split(",");
            String name = fields[0], vendor = fields[1], variant = fields[2];
            boolean dh = Boolean.parseBoolean(fields[3]);
            Path output = Files.createDirectories(work.resolve("export").resolve(name));
            var options = new Properties();
            try (var reader = Files.newBufferedReader(work.resolve(variant + ".properties"))) {
                options.load(reader);
            }
            Map<String, String> selections = new HashMap<>();
            options.stringPropertyNames().forEach(key -> selections.put(key, options.getProperty(key)));
            var defines = environment(vendor, dh);
            ShaderPack pack;
            auditing = true;
            try {
                pack = new ShaderPack(work.resolve("shaders"), selections, defines);
            } finally {
                auditing = false;
            }
            Files.write(output.resolve("environment.txt"), defines.stream().map(pair -> pair.key() + "=" + pair.value()).toList());
            Files.write(output.resolve("images.txt"), pack.getIrisCustomImages().stream().map(Object::toString).toList());
            exportDimension(output.resolve("overworld"), pack.getProgramSet(DimensionId.OVERWORLD), dh);
            exportDimension(output.resolve("nether"), pack.getProgramSet(DimensionId.NETHER), dh);
            exportDimension(output.resolve("end"), pack.getProgramSet(DimensionId.END), dh);
            Iris.logger.info("Bliss Windows audit exported {}", name);
        }
    }

    private static ImmutableList<StringPair> environment(String vendor, boolean dh) {
        var defines = ImmutableList.<StringPair>builder();
        StandardMacros.createStandardEnvironmentDefines().stream().filter(pair -> !isHostDefine(pair.key())).forEach(defines::add);
        defines.add(new StringPair("MC_OS_WINDOWS", ""), new StringPair("MC_GL_VERSION", "460"),
                new StringPair("MC_GLSL_VERSION", "460"), new StringPair("MC_GL_VENDOR_" + vendor, ""),
                new StringPair("MC_GL_RENDERER_" + switch (vendor) {
                    case "NVIDIA" -> "GEFORCE";
                    case "AMD" -> "RADEON";
                    default -> "INTEL";
                }, ""), new StringPair("IRIS_FEATURE_CUSTOM_IMAGES", ""), new StringPair("IRIS_FEATURE_COMPUTE_SHADERS", ""),
                new StringPair("MC_GL_ARB_shader_image_load_store", ""), new StringPair("MC_GL_ARB_compute_shader", ""),
                new StringPair("AURAM_BLISS_LPV_BACKEND", ""), new StringPair("AURAM_BLISS_WARP_MIST", ""),
                new StringPair("AURAM_RF_BACKEND", ""), new StringPair("AURAM_BLISS_RF_LPV", ""),
                new StringPair("AURAM_BLISS_ASTRAL_SKY", ""));
        if (Boolean.getBoolean("auram.bliss.qa.auditPreparedRf")) {
            defines.add(new StringPair("AURAM_BLISS_RF_PREPARED", ""));
        }
        if (dh) defines.add(new StringPair("DISTANT_HORIZONS", ""));
        return defines.build();
    }

    private static void exportDimension(Path path, ProgramSet programs, boolean dh) throws IOException {
        fullscreen(path, TextureStage.BEGIN, programs.getBegin());
        fullscreen(path, TextureStage.PREPARE, programs.getPrepare());
        fullscreen(path, TextureStage.DEFERRED, programs.getDeferred());
        fullscreen(path, TextureStage.SHADOWCOMP, programs.getShadowComposite());
        fullscreen(path, TextureStage.COMPOSITE_AND_FINAL, programs.getComposite());
        fullscreen(path, TextureStage.COMPOSITE_AND_FINAL, programs.getCompositeFinal().orElse(null));
        compute(path, TextureStage.SETUP, programs.getSetup());
        for (var pass : programs.getShadowCompCompute()) compute(path, TextureStage.SHADOWCOMP, pass);
        var terrain = programs.getGbuffersTerrain().orElseThrow();
        sodium(path.resolve("terrain-solid"), programs.getGbuffersTerrainSolid().orElse(terrain), AlphaTest.ALWAYS);
        sodium(path.resolve("terrain-cutout"), programs.getGbuffersTerrainCutout().orElse(terrain), AlphaTests.ONE_TENTH_ALPHA);
        sodium(path.resolve("terrain-water"), programs.getGbuffersWater().orElse(terrain), AlphaTest.ALWAYS);
        var shadow = programs.getShadow().orElse(null);
        if (shadow != null) {
            sodium(path.resolve("shadow-solid"), shadow, AlphaTest.ALWAYS);
            sodium(path.resolve("shadow-cutout"), shadow, AlphaTests.ONE_TENTH_ALPHA);
        }
        var sky = programs.getGbuffersSkyTextured().orElse(null);
        if (sky != null) {
            save(path.resolve(sky.getName()), TransformPatcher.patchVanilla(sky.getName(), vertex(sky), null, null,
                    geometry(sky), fragment(sky), AlphaTest.ALWAYS, false, false,
                    new ShaderAttributeInputs(true, true, false, false, false), textures(sky)));
        }
        if (dh) {
            for (var source : Arrays.asList(programs.getDhTerrain(), programs.getDhWater(), programs.getDhShadow())) {
                if (source.isEmpty()) continue;
                var shader = source.get();
                save(path.resolve(shader.getName()), TransformPatcher.patchDHTerrain(shader.getName(), vertex(shader),
                        null, null, geometry(shader), fragment(shader), textures(shader)));
            }
        }
    }

    private static void fullscreen(Path path, TextureStage stage, ProgramSource... programs) throws IOException {
        for (var shader : programs) {
            if (shader == null || !shader.isValid()) continue;
            save(path.resolve(shader.getName()), TransformPatcher.patchComposite(shader.getName(), vertex(shader),
                    geometry(shader), fragment(shader), stage, textures(shader)));
        }
    }

    private static void compute(Path path, TextureStage stage, ComputeSource[] programs) throws IOException {
        if (programs == null) return;
        for (var shader : programs) {
            if (shader == null || !shader.isValid()) continue;
            String source = shader.getSource().orElseThrow();
            // This release leaves empty compute wrappers after the image declarations are disabled.
            if (!source.contains("local_size_")) continue;
            String transformed = TransformPatcher.patchCompute(shader.getName(), source, stage,
                    shader.getParent().getPackDirectives().getTextureMap());
            save(path.resolve("compute-" + shader.getName()), Map.of(PatchShaderType.COMPUTE, transformed));
        }
    }

    private static void sodium(Path path, ProgramSource shader, AlphaTest alpha) throws IOException {
        save(path, TransformPatcher.patchSodium(shader.getName(), vertex(shader), null, null, geometry(shader), fragment(shader),
                shader.getDirectives().getAlphaTestOverride().orElse(alpha),
                new ShaderAttributeInputs(true, true, false, true, true), textures(shader)));
    }

    private static String vertex(ProgramSource shader) { return shader.getVertexSource().orElse(null); }

    private static String geometry(ProgramSource shader) { return shader.getGeometrySource().orElse(null); }

    private static String fragment(ProgramSource shader) { return shader.getFragmentSource().orElse(null); }

    private static Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textures(ProgramSource shader) {
        return shader.getParent().getPackDirectives().getTextureMap();
    }

    private static void save(Path path, Map<PatchShaderType, String> stages) throws IOException {
        Files.createDirectories(path);
        for (var stage : stages.entrySet()) {
            if (stage.getValue() == null) continue;
            String extension = switch (stage.getKey()) {
                case VERTEX -> "vert";
                case FRAGMENT -> "frag";
                case GEOMETRY -> "geom";
                case TESS_CONTROL -> "tesc";
                case TESS_EVAL -> "tese";
                case COMPUTE -> "comp";
            };
            Files.writeString(path.resolve("shader." + extension), stage.getValue());
        }
    }
}
