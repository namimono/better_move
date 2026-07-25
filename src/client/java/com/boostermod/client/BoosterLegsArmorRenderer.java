package com.boostermod.client;

import com.boostermod.BoosterMod;
import com.boostermod.item.BoosterLeggingsItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 护腿槽穿戴外观：用原版护甲内层模型画等级贴图，不依赖 ArmorItem。
 */
public final class BoosterLegsArmorRenderer implements ArmorRenderer {
    public static void register() {
        ArmorRenderer.register(
                new BoosterLegsArmorRenderer(),
                BoosterMod.BOOSTER_LEGGINGS_COPPER,
                BoosterMod.BOOSTER_LEGGINGS_IRON,
                BoosterMod.BOOSTER_LEGGINGS_GOLD,
                BoosterMod.BOOSTER_LEGGINGS_DIAMOND,
                BoosterMod.BOOSTER_LEGGINGS_NETHERITE);
    }

    @Override
    public void render(
            PoseStack matrices,
            MultiBufferSource vertexConsumers,
            ItemStack stack,
            LivingEntity entity,
            EquipmentSlot slot,
            int light,
            HumanoidModel<LivingEntity> contextModel) {
        if (slot != EquipmentSlot.LEGS) {
            return;
        }
        if (!(stack.getItem() instanceof BoosterLeggingsItem item)) {
            return;
        }

        BoosterWornLegsAppearance.render(
                matrices, vertexConsumers, stack, item.getTier(), light, contextModel);
    }
}
