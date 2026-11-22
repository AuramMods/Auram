package dev.auram.photon.compat.mixin;

import com.google.common.collect.ImmutableSet;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = CompositeRenderer.class, remap = false)
abstract class IrisBlissCompositeUnitsMixin {
    @ModifyArg(method = "createProgram", index = 4, at = @At(value = "INVOKE", target =
            "Lnet/irisshaders/iris/gl/program/ProgramBuilder;begin(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/google/common/collect/ImmutableSet;)Lnet/irisshaders/iris/gl/program/ProgramBuilder;"))
    private ImmutableSet<Integer> auramBliss$useCompositeTextureUnits(ImmutableSet<Integer> reserved) {
        String shader = Iris.getCurrentPackName();
        if ("AuramBliss".equals(shader) || "AuramBliss.zip".equals(shader)) {
            // CompositeRenderer binds all of its textures dynamically. The lightmap and overlay
            // reservations are only consumed by the world/geometry sampler setup.
            return ImmutableSet.of();
        }
        return reserved;
    }
}
