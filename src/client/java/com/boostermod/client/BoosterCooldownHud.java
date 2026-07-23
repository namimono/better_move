package com.boostermod.client;

import com.boostermod.combat.BoostStrikeSupport;
import com.boostermod.item.BoosterEquipment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class BoosterCooldownHud {
    private BoosterCooldownHud() {}

    static void render(GuiGraphics drawContext) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.options.hideGui || !BoosterHudState.isEnabled()) {
            return;
        }

        BoosterEquipment.Equipped equipped = BoosterEquipment.find(player).orElse(null);
        if (equipped == null) {
            return;
        }

        ItemStack legs = equipped.stack();
        Item item = equipped.item();

        float cooldownRemaining = player.getCooldowns().getCooldownPercent(item, 0.0f);
        boolean ready = cooldownRemaining <= 0.0f;
        if (!ready && cooldownRemaining >= 1.0f) {
            return;
        }

        boolean showStack = BoostStrikeSupport.hasBoostStrikeUpgrade(player);
        if (!showStack && BoostStrikeStackState.hasActiveStack()) {
            // 卸下破击后不占叠层轨，并清本地快照
            BoostStrikeStackState.reset();
        }

        float charge = ready ? 1.0f : 1.0f - cooldownRemaining;
        BoosterHudRenderer.render(
                drawContext,
                charge,
                ready,
                durabilityPercent(legs),
                player.tickCount,
                showStack,
                showStack ? BoostStrikeStackState.stackRatio() : 0.0f,
                showStack ? BoostStrikeStackState.timeRatio() : 0.0f);
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
