package dev.auram.photon.compat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.auram.photon.compat.chest.CrystalChestMeshes;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.moepus.flerovium.functions.FastSimpleBakedModelRenderer", remap = false)
abstract class FleroviumChestItemModelsMixin {
    @Unique private static boolean auramBliss$capturing;

    @Shadow
    public static void render(SimpleBakedModel model, int faces, ItemStack stack, int light, int overlay,
                              PoseStack pose, VertexBufferWriter writer, ItemColors colors) { throw new AssertionError(); }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private static void auramBliss$batchChestModel(SimpleBakedModel model, int faces, ItemStack stack, int light, int overlay,
                                                  PoseStack pose, VertexBufferWriter writer, ItemColors colors,
                                                  CallbackInfo callback) {
        if (auramBliss$capturing || !CrystalChestMeshes.inChest() || stack.m_41790_()) return;
        auramBliss$capturing = true;
        try {
            if (CrystalChestMeshes.flerovium().drawFlerovium(model, faces, stack, light, overlay, pose.m_85850_(), writer, colors,
                    FleroviumChestItemModelsMixin::auramBliss$captureMesh)) {
                callback.cancel();
            }
        } finally {
            auramBliss$capturing = false;
        }
    }

    @Unique
    private static void auramBliss$captureMesh(BakedModel model, int faces, ItemStack item, int light, int overlay,
                                               PoseStack pose, VertexConsumer consumer, ItemColors colors) {
        render((SimpleBakedModel) model, faces, item, light, overlay, pose, (VertexBufferWriter) consumer, colors);
    }
}
