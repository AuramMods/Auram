package dev.auram.bliss.test;

import com.google.common.collect.ImmutableList;
import dev.auram.bliss.qa.WindowsShaders;
import net.irisshaders.iris.helpers.StringPair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.irisshaders.iris.shaderpack.IrisDefines", remap = false)
abstract class AuditDefines {
    @Inject(method = "createIrisReplacements", at = @At("RETURN"), cancellable = true)
    private static void filterHostGraphicsDefines(CallbackInfoReturnable<ImmutableList<StringPair>> callback) {
        if (WindowsShaders.isAuditing()) {
            callback.setReturnValue(callback.getReturnValue().stream()
                    .filter(pair -> !WindowsShaders.isHostDefine(pair.key()))
                    .collect(ImmutableList.toImmutableList()));
        }
    }
}
