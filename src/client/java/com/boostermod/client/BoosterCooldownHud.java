package com.boostermod.client;

import com.boostermod.charge.ChargeSession;
import com.boostermod.combat.BoostStrikeSupport;
import com.boostermod.item.BoosterEquipment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class BoosterCooldownHud {
    private static final int CHARGE_FADE_TICKS = 4;
    private static float chargeAppear;

    private BoosterCooldownHud() {}

    static void render(GuiGraphics drawContext) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.options.hideGui || !BoosterHudState.isEnabled()) {
            chargeAppear = 0.0f;
            return;
        }

        BoosterEquipment.Equipped equipped = BoosterEquipment.find(player).orElse(null);
        if (equipped == null) {
            chargeAppear = 0.0f;
            return;
        }

        ItemStack legs = equipped.stack();
        Item item = equipped.item();

        float cooldownRemaining = player.getCooldowns().getCooldownPercent(item, 0.0f);
        boolean ready = cooldownRemaining <= 0.0f;
        if (!ready && cooldownRemaining >= 1.0f) {
            chargeAppear = 0.0f;
            return;
        }

        boolean showStack = BoostStrikeSupport.hasBoostStrikeUpgrade(player);
        if (!showStack && BoostStrikeStackState.hasActiveStack()) {
            BoostStrikeStackState.reset();
        }

        boolean charging = BoosterInputHandler.isLocalCharging();
        int chargeTicks = BoosterInputHandler.localChargeTicks(player);
        float targetAppear = charging ? 1.0f : 0.0f;
        float appearStep = 1.0f / CHARGE_FADE_TICKS;
        if (chargeAppear < targetAppear) {
            chargeAppear = Math.min(targetAppear, chargeAppear + appearStep);
        } else if (chargeAppear > targetAppear) {
            chargeAppear = Math.max(targetAppear, chargeAppear - appearStep);
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
                showStack ? BoostStrikeStackState.timeRatio() : 0.0f,
                chargeAppear,
                chargeTicks,
                ChargeSession.CHARGE_DURATION_TICKS,
                ChargeSession.MAX_CHARGE_TICKS);
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
