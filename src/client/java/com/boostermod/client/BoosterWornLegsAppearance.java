package com.boostermod.client;

import com.boostermod.appearance.BoosterAppearanceTextures;
import com.boostermod.tier.BoosterTier;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 护腿部位穿戴外观绘制：护腿槽与饰品槽共用，贴图只走 {@link BoosterAppearanceTextures}。
 */
public final class BoosterWornLegsAppearance {
    private static HumanoidModel<LivingEntity> legsModel;

    private BoosterWornLegsAppearance() {}

    public static void render(
            PoseStack matrices,
            MultiBufferSource vertexConsumers,
            ItemStack stack,
            BoosterTier tier,
            int light,
            HumanoidModel<LivingEntity> poseSource) {
        HumanoidModel<LivingEntity> model = model();
        poseSource.copyPropertiesTo(model);
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
                BoosterAppearanceTextures.wornTexture(tier));
    }

    private static HumanoidModel<LivingEntity> model() {
        if (legsModel == null) {
            legsModel = new HumanoidModel<>(
                    Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        }
        return legsModel;
    }
}
