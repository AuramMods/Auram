package dev.auram.photon.compat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.auram.photon.compat.chest.CrystalChestMeshes;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemRenderer.class, remap = false)
abstract class CrystalChestItemModelsMixin {
    @Unique private final CrystalChestMeshes auramBliss$meshes = new CrystalChestMeshes();
    @Shadow @Final private ItemColors f_115097_;

    @Shadow
    private void m_115189_(BakedModel model, ItemStack stack, int light, int overlay, PoseStack pose,
                           VertexConsumer consumer) { throw new AssertionError(); }

    @Redirect(method = "m_115143_", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;"
            + "m_115189_(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemStack;II"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"))
    private void auramBliss$drawChestMesh(ItemRenderer renderer, BakedModel model, ItemStack stack, int light,
                                         int overlay, PoseStack pose, VertexConsumer consumer) {
        if (!CrystalChestMeshes.inChest() || stack.m_41790_() || !auramBliss$meshes.draw(
                model, stack, light, overlay, pose.m_85850_(), consumer, f_115097_, this::m_115189_)) {
            m_115189_(model, stack, light, overlay, pose, consumer);
        }
    }

    @Inject(method = "m_6213_", at = @At("TAIL"))
    private void auramBliss$clearChestMeshes(ResourceManager resources, CallbackInfo callback) {
        auramBliss$meshes.clear();
    }
}
