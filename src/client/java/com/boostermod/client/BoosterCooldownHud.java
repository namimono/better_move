package com.boostermod.client;

import com.boostermod.item.BoosterLeggingsItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class BoosterCooldownHud {
    private BoosterCooldownHud() {}

    static void render(GuiGraphics drawContext) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.options.hideGui) {
            return;
        }

        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        Item item = legs.getItem();
        if (!(item instanceof BoosterLeggingsItem)) {
            return;
        }

        float cooldownRemaining = player.getCooldowns().getCooldownPercent(item, 0.0f);
        boolean ready = cooldownRemaining <= 0.0f;
        if (!ready && cooldownRemaining >= 1.0f) {
            return;
        }

        float charge = ready ? 1.0f : 1.0f - cooldownRemaining;
        BoosterHudRenderer.render(drawContext, charge, ready, durabilityPercent(legs), player.tickCount);
    }

    private static float durabilityPercent(ItemStack stack) {
        if (!stack.isDamageableItem()) {
            return 1.0f;
        }

        int maxDamage = stack.getMaxDamage();
        if (maxDamage <= 0) {
            return 1.0f;
        }

        return Math.max(0.0f, (maxDamage - stack.getDamageValue()) / (float) maxDamage);
    }
}
