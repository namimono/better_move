package com.boostermod.client.compat;

import com.boostermod.client.BoosterWornLegsAppearance;
import com.boostermod.item.BoosterLeggingsItem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.client.TrinketRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 饰品槽穿戴外观：与护腿槽共用 {@link BoosterWornLegsAppearance} / 等级贴图定位。
 */
public final class BoosterTrinketRenderer implements TrinketRenderer {
    @Override
    @SuppressWarnings("unchecked")
    public void render(
            ItemStack stack,
            SlotReference slotReference,
            EntityModel<? extends LivingEntity> contextModel,
            PoseStack matrices,
            MultiBufferSource vertexConsumers,
            int light,
            LivingEntity entity,
            float limbAngle,
            float limbDistance,
            float tickDelta,
            float animationProgress,
            float headYaw,
            float headPitch) {
        if (!(stack.getItem() instanceof BoosterLeggingsItem item)) {
            return;
        }
        if (!(contextModel instanceof HumanoidModel<?> humanoid)) {
            return;
        }

        BoosterWornLegsAppearance.render(
                matrices,
                vertexConsumers,
                stack,
                item.getTier(),
                light,
                (HumanoidModel<LivingEntity>) humanoid);
    }
}
