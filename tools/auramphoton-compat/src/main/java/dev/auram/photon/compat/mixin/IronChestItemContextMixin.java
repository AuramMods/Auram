package dev.auram.photon.compat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.auram.photon.compat.chest.CrystalChestMeshes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.progwml6.ironchest.client.render.IronChestRenderer", remap = false)
abstract class IronChestItemContextMixin {
    @Redirect(method = "renderItem", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;"
            + "m_269128_(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;II"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;I)V"))
    private static void auramBliss$renderChestItem(ItemRenderer renderer, ItemStack stack, ItemDisplayContext display,
                                                  int light, int overlay, PoseStack pose, MultiBufferSource batches,
                                                  Level level, int seed) {
        boolean previous = CrystalChestMeshes.beginChest();
        try {
            renderer.m_269128_(stack, display, light, overlay, pose, batches, level, seed);
        } finally {
            CrystalChestMeshes.endChest(previous);
        }
    }
}
