package com.boostermod.client;

import com.boostermod.charge.ChargeSession;
import com.boostermod.combat.BoostStrikeSupport;
import com.boostermod.hud.ChargeAppearAnimation;
import com.boostermod.item.BoosterEquipment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class BoosterCooldownHud {
    private static float chargeAppear;
    private static int lastAppearTick = Integer.MIN_VALUE;
    /** 退场淡出期间保留最后蓄力进度，避免条瞬间变空。 */
    private static int lastShownChargeTicks;

    private BoosterCooldownHud() {}

    static void render(GuiGraphics drawContext) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.options.hideGui || !BoosterHudState.isEnabled()) {
            resetAppear();
            return;
        }

        BoosterEquipment.Equipped equipped = BoosterEquipment.find(player).orElse(null);
        if (equipped == null) {
            resetAppear();
            return;
        }

        ItemStack legs = equipped.stack();
        Item item = equipped.item();

        float cooldownRemaining = player.getCooldowns().getCooldownPercent(item, 0.0f);
        boolean ready = cooldownRemaining <= 0.0f;

        boolean showStack = BoostStrikeSupport.hasBoostStrikeUpgrade(player);
        if (!showStack && BoostStrikeStackState.hasActiveStack()) {
            BoostStrikeStackState.reset();
        }

        boolean charging = BoosterInputHandler.isLocalCharging();
        int chargeTicks = BoosterInputHandler.localChargeTicks(player);
        if (charging) {
            lastShownChargeTicks = chargeTicks;
        }

        int gameTick = player.tickCount;
        float nextAppear = ChargeAppearAnimation.stepOnTick(chargeAppear, charging, gameTick, lastAppearTick);
        if (gameTick != lastAppearTick) {
            lastAppearTick = gameTick;
        }
        chargeAppear = nextAppear;
        if (!ChargeAppearAnimation.shouldRender(chargeAppear)) {
            lastShownChargeTicks = 0;
        }

        // 满 CD 时主面板可隐藏，但蓄力轨退场动画必须跑完（禁止硬切）。
        boolean hideMainPanel = !ready && cooldownRemaining >= 1.0f;
        if (hideMainPanel && !ChargeAppearAnimation.shouldRender(chargeAppear)) {
            return;
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
                lastShownChargeTicks,
                ChargeSession.CHARGE_DURATION_TICKS,
                ChargeSession.MAX_CHARGE_TICKS,
                !hideMainPanel);
    }

    private static void resetAppear() {
        chargeAppear = 0.0f;
        lastAppearTick = Integer.MIN_VALUE;
        lastShownChargeTicks = 0;
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
