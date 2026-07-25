package com.boostermod.client;

import com.boostermod.BoosterMod;
import com.boostermod.appearance.BoosterAppearanceTextures;
import com.boostermod.item.BoosterLeggingsItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 护腿槽穿戴外观：用原版护甲内层模型画等级贴图，不依赖 ArmorItem。
 */
public final class BoosterLegsArmorRenderer implements ArmorRenderer {
    private HumanoidModel<LivingEntity> legsModel;

    public static void register() {
        BoosterLegsArmorRenderer renderer = new BoosterLegsArmorRenderer();
        ArmorRenderer.register(
                renderer,
                BoosterMod.BOOSTER_LEGGINGS_COPPER,
                BoosterMod.BOOSTER_LEGGINGS_IRON,
                BoosterMod.BOOSTER_LEGGINGS_GOLD,
                BoosterMod.BOOSTER_LEGGINGS_DIAMOND,
                BoosterMod.BOOSTER_LEGGINGS_NETHERITE);
    }

    private HumanoidModel<LivingEntity> legsModel() {
        if (legsModel == null) {
            legsModel = new HumanoidModel<>(
                    Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        }
        return legsModel;
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

        HumanoidModel<LivingEntity> model = legsModel();
        contextModel.copyPropertiesTo(model);
        model.setAllVisible(false);
        model.body.visible = true;
        model.rightLeg.visible = true;
        model.leftLeg.visible = true;
        ArmorRenderer.renderPart(
                matrices,
                vertexConsumers,
                light,
                stack,
                model,
                BoosterAppearanceTextures.wornTexture(item.getTier()));
    }
}
