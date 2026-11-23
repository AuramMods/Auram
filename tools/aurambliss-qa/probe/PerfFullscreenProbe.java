package dev.auram.bliss.test;

import dev.auram.bliss.qa.PassMetrics;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.irisshaders.iris.pipeline.FinalPassRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = {CompositeRenderer.class, FinalPassRenderer.class}, remap = false)
abstract class PerfFullscreenProbe {
    @Redirect(method = {"renderAll", "renderFinalPass"}, at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V"))
    private void timeFullscreenDraw(FullScreenQuadRenderer renderer) {
        var span = PassMetrics.beginProgram("draw");
        renderer.renderQuad();
        PassMetrics.end(span);
    }
}
