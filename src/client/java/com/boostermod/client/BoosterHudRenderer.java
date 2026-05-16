package com.boostermod.client;

import net.minecraft.client.gui.GuiGraphics;

final class BoosterHudRenderer {
    private static final int HUD_WIDTH = 12;
    private static final int HUD_HEIGHT = 22;
    private static final int HOTBAR_HALF_WIDTH = 91;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int HOTBAR_RIGHT_MARGIN = 4;
    private static final int PANEL_OUTLINE = 0xD2A8B6C7;
    private static final int PANEL_SHADOW = 0x78000000;
    private static final int PANEL_FILL = 0xB0121822;
    private static final int CORE_FILL = 0xD01D2430;
    private static final int BAR_BACKGROUND = 0x60303A46;
    private static final int BAR_DIVIDER = 0x80566472;
    private static final int COOLDOWN_CHARGING = 0xFFE9A14A;
    private static final int COOLDOWN_READY = 0xFF79E8FF;
    private static final int COOLDOWN_READY_GLOW = 0xFFDFFBFF;
    private static final int DURABILITY_HIGH = 0xFF7CFFA7;
    private static final int DURABILITY_MID = 0xFFF5D96A;
    private static final int DURABILITY_LOW = 0xFFFF7A5C;
    private static final int THRUSTER_IDLE = 0xFF667385;
    private static final int THRUSTER_CHARGING = 0xFFCF7B2A;
    private static final int THRUSTER_READY = 0xFF60DFFF;

    private BoosterHudRenderer() {}

    static void render(GuiGraphics drawContext, float charge, boolean ready, float durability, int tickCount) {
        int hotbarLeft = drawContext.guiWidth() / 2 - HOTBAR_HALF_WIDTH;
        int hotbarTop = drawContext.guiHeight() - HOTBAR_HEIGHT;
        int x = hotbarLeft + HOTBAR_HALF_WIDTH * 2 + HOTBAR_RIGHT_MARGIN;
        int y = hotbarTop;

        int leftThruster = ready ? THRUSTER_READY : blendColor(THRUSTER_IDLE, THRUSTER_CHARGING, charge);
        int rightThruster = ready ? THRUSTER_READY : blendColor(THRUSTER_IDLE, THRUSTER_CHARGING, Math.min(1.0f, charge + 0.12f));

        drawContext.fill(x, y + 1, x + HUD_WIDTH, y + HUD_HEIGHT + 1, PANEL_SHADOW);
        drawContext.fill(x, y, x + HUD_WIDTH, y + HUD_HEIGHT, PANEL_OUTLINE);
        drawContext.fill(x + 1, y + 1, x + HUD_WIDTH - 1, y + HUD_HEIGHT - 1, PANEL_FILL);

        drawContext.fill(x + 2, y + 1, x + 5, y + 3, leftThruster);
        drawContext.fill(x + HUD_WIDTH - 5, y + 1, x + HUD_WIDTH - 2, y + 3, rightThruster);

        int coreLeft = x + 2;
        int coreRight = x + HUD_WIDTH - 2;
        int coreTop = y + 4;
        int coreBottom = y + HUD_HEIGHT - 2;
        drawContext.fill(coreLeft, coreTop, coreRight, coreBottom, CORE_FILL);
        drawContext.fill(coreLeft + 3, coreTop, coreLeft + 4, coreBottom, BAR_DIVIDER);

        int barTop = coreTop + 1;
        int barBottom = coreBottom - 1;
        int cooldownLeft = coreLeft + 1;
        int cooldownRight = coreLeft + 3;
        int durabilityLeft = coreLeft + 5;
        int durabilityRight = coreRight - 1;

        drawContext.fill(cooldownLeft, barTop, cooldownRight, barBottom, BAR_BACKGROUND);
        drawContext.fill(durabilityLeft, barTop, durabilityRight, barBottom, BAR_BACKGROUND);

        drawVerticalMeter(
                drawContext,
                cooldownLeft,
                cooldownRight,
                barTop,
                barBottom,
                charge,
                ready ? COOLDOWN_READY : COOLDOWN_CHARGING);

        int durabilityColor = durability > 0.55f
                ? blendColor(DURABILITY_MID, DURABILITY_HIGH, (durability - 0.55f) / 0.45f)
                : blendColor(DURABILITY_LOW, DURABILITY_MID, durability / 0.55f);
        drawVerticalMeter(
                drawContext,
                durabilityLeft,
                durabilityRight,
                barTop,
                barBottom,
                durability,
                durabilityColor);

        if (ready) {
            int pulse = 35 + (tickCount % 10) * 6;
            int glow = (Math.min(0x7F, pulse) << 24) | (COOLDOWN_READY_GLOW & 0x00FFFFFF);
            drawContext.fill(cooldownLeft, y - 1, cooldownRight, y, glow);
        }
    }

    private static void drawVerticalMeter(
            GuiGraphics drawContext,
            int left,
            int right,
            int top,
            int bottom,
            float amount,
            int fillColor) {
        float clamped = Math.max(0.0f, Math.min(1.0f, amount));
        int height = bottom - top;
        int filled = Math.max(0, Math.min(height, Math.round(height * clamped)));
        if (filled <= 0) {
            return;
        }

        drawContext.fill(left, bottom - filled, right, bottom, fillColor);
    }

    private static int blendColor(int from, int to, float progress) {
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        int fromA = from >>> 24;
        int fromR = (from >>> 16) & 0xFF;
        int fromG = (from >>> 8) & 0xFF;
        int fromB = from & 0xFF;
        int toA = to >>> 24;
        int toR = (to >>> 16) & 0xFF;
        int toG = (to >>> 8) & 0xFF;
        int toB = to & 0xFF;

        int a = Math.round(fromA + (toA - fromA) * clamped);
        int r = Math.round(fromR + (toR - fromR) * clamped);
        int g = Math.round(fromG + (toG - fromG) * clamped);
        int b = Math.round(fromB + (toB - fromB) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
