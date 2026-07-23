package com.boostermod.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 热键栏右侧推进 HUD。
 * <p>
 * 双轨：冷却 | 耐久；有破击时：冷却 | 耐久 | 叠层。
 * 每轨顶有 3×3 语义图标，叠层轨用分段刻度，避免「只剩颜色条」看不懂。
 */
final class BoosterHudRenderer {
    /** 双轨外框：与历史 12px 一致。 */
    private static final int HUD_WIDTH_DUAL = 12;
    /** 三轨：在右侧加固定宽叠层轨，不挤占耐久宽度。 */
    private static final int HUD_WIDTH_TRIPLE = 17;
    private static final int HUD_HEIGHT = 22;
    private static final int HOTBAR_HALF_WIDTH = 91;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int HOTBAR_RIGHT_MARGIN = 4;

    private static final int TRACK_CD = 2;
    private static final int TRACK_DUR = 3;
    private static final int TRACK_ST = 2;
    private static final int TRACK_GAP = 1;

    private static final int PANEL_OUTLINE = 0xD2A8B6C7;
    private static final int PANEL_SHADOW = 0x78000000;
    private static final int PANEL_FILL = 0xB0121822;
    private static final int CORE_FILL = 0xD01D2430;
    private static final int BAR_BACKGROUND = 0x70303A46;
    private static final int BAR_WELL = 0x40000000;
    private static final int ICON_DIM = 0xFF4A5564;

    private static final int COOLDOWN_CHARGING = 0xFFE9A14A;
    private static final int COOLDOWN_READY = 0xFF79E8FF;
    private static final int COOLDOWN_READY_GLOW = 0xFFDFFBFF;
    private static final int COOLDOWN_ICON = 0xFF8FDFFF;

    private static final int DURABILITY_HIGH = 0xFF7CFFA7;
    private static final int DURABILITY_MID = 0xFFF5D96A;
    private static final int DURABILITY_LOW = 0xFFFF7A5C;
    private static final int DURABILITY_ICON = 0xFF7CFFA7;

    private static final int THRUSTER_IDLE = 0xFF667385;
    private static final int THRUSTER_CHARGING = 0xFFCF7B2A;
    private static final int THRUSTER_READY = 0xFF60DFFF;

    private static final int STACK_LOW = 0xFFE07A4A;
    private static final int STACK_MID = 0xFFE85A9A;
    private static final int STACK_FULL = 0xFFFF4DDE;
    private static final int STACK_TIME_GLOW = 0xFFFFB0F0;
    private static final int STACK_ICON = 0xFFFF6EC7;
    private static final int STACK_NOTCH = 0x55201828;
    /** 叠层分段数：像「层」而不是油量。 */
    private static final int STACK_SEGMENTS = 4;

    private BoosterHudRenderer() {}

    static void render(
            GuiGraphics drawContext,
            float charge,
            boolean ready,
            float durability,
            int tickCount,
            boolean showStack,
            float stackRatio,
            float stackTimeRatio) {
        int hudWidth = showStack ? HUD_WIDTH_TRIPLE : HUD_WIDTH_DUAL;
        int hotbarLeft = drawContext.guiWidth() / 2 - HOTBAR_HALF_WIDTH;
        int hotbarTop = drawContext.guiHeight() - HOTBAR_HEIGHT;
        int x = hotbarLeft + HOTBAR_HALF_WIDTH * 2 + HOTBAR_RIGHT_MARGIN;
        int y = hotbarTop;

        int leftThruster = ready ? THRUSTER_READY : blendColor(THRUSTER_IDLE, THRUSTER_CHARGING, charge);
        int rightThruster = ready
                ? THRUSTER_READY
                : blendColor(THRUSTER_IDLE, THRUSTER_CHARGING, Math.min(1.0f, charge + 0.12f));

        drawContext.fill(x, y + 1, x + hudWidth, y + HUD_HEIGHT + 1, PANEL_SHADOW);
        drawContext.fill(x, y, x + hudWidth, y + HUD_HEIGHT, PANEL_OUTLINE);
        drawContext.fill(x + 1, y + 1, x + hudWidth - 1, y + HUD_HEIGHT - 1, PANEL_FILL);

        // 顶部推进器灯（面板语义：这是推进器模块）
        drawContext.fill(x + 2, y + 1, x + 5, y + 3, leftThruster);
        drawContext.fill(x + hudWidth - 5, y + 1, x + hudWidth - 2, y + 3, rightThruster);

        int coreLeft = x + 2;
        int coreRight = x + hudWidth - 2;
        int coreTop = y + 4;
        int coreBottom = y + HUD_HEIGHT - 2;
        drawContext.fill(coreLeft, coreTop, coreRight, coreBottom, CORE_FILL);

        // 图标带 + 条带分区：图标在上，计量条在下
        int iconBandTop = coreTop + 1;
        int iconBandBottom = iconBandTop + 3;
        int barTop = iconBandBottom + 1;
        int barBottom = coreBottom - 1;

        // 布局：CD | DUR [| ST] —— 叠层在耐久右侧；轨宽固定，不把「剩余宽度」全塞给耐久
        int cursor = coreLeft + 1;
        int cdLeft = cursor;
        int cdRight = cdLeft + TRACK_CD;
        cursor = cdRight + TRACK_GAP;
        int durLeft = cursor;
        int durRight = durLeft + TRACK_DUR;
        cursor = durRight + TRACK_GAP;
        int stLeft = cursor;
        int stRight = stLeft + TRACK_ST;

        // 轨井背景
        drawTrackWell(drawContext, cdLeft, cdRight, iconBandTop, barBottom);
        drawTrackWell(drawContext, durLeft, durRight, iconBandTop, barBottom);
        if (showStack) {
            drawTrackWell(drawContext, stLeft, stRight, iconBandTop, barBottom);
        }

        // 语义图标（始终显示，即使条为空也能认）
        int cdIconColor = ready ? COOLDOWN_READY : blendColor(ICON_DIM, COOLDOWN_ICON, 0.55f + 0.45f * charge);
        drawIconFlame(drawContext, cdLeft, iconBandTop, cdIconColor);

        int durabilityColor = durability > 0.55f
                ? blendColor(DURABILITY_MID, DURABILITY_HIGH, (durability - 0.55f) / 0.45f)
                : blendColor(DURABILITY_LOW, DURABILITY_MID, durability / 0.55f);
        int durIconColor = blendColor(ICON_DIM, DURABILITY_ICON, 0.4f + 0.6f * Math.max(0.15f, durability));
        drawIconHeart(drawContext, durLeft + 1, iconBandTop, durIconColor);

        if (showStack) {
            float clampedStack = Math.max(0.0f, Math.min(1.0f, stackRatio));
            int stackIconColor = clampedStack > 0.01f
                    ? blendColor(STACK_LOW, STACK_FULL, clampedStack)
                    : blendColor(ICON_DIM, STACK_ICON, 0.35f);
            drawIconBlade(drawContext, stLeft, iconBandTop, stackIconColor);
        }

        // 计量条
        drawVerticalMeter(
                drawContext,
                cdLeft,
                cdRight,
                barTop,
                barBottom,
                charge,
                ready ? COOLDOWN_READY : COOLDOWN_CHARGING);

        drawVerticalMeter(
                drawContext,
                durLeft,
                durRight,
                barTop,
                barBottom,
                durability,
                durabilityColor);

        if (showStack) {
            float clampedStack = Math.max(0.0f, Math.min(1.0f, stackRatio));
            int stackColor = clampedStack >= 0.99f
                    ? STACK_FULL
                    : clampedStack > 0.5f
                            ? blendColor(STACK_MID, STACK_FULL, (clampedStack - 0.5f) / 0.5f)
                            : blendColor(STACK_LOW, STACK_MID, clampedStack / 0.5f);

            drawSegmentedMeter(
                    drawContext,
                    stLeft,
                    stRight,
                    barTop,
                    barBottom,
                    clampedStack,
                    stackColor);

            // 寿命：条顶 1px，亮度 ∝ 剩余时间
            float time = Math.max(0.0f, Math.min(1.0f, stackTimeRatio));
            if (clampedStack > 0.0f && time > 0.0f) {
                int timeAlpha = Math.round(0x50 + 0xAF * time);
                int timeColor = (timeAlpha << 24) | (STACK_TIME_GLOW & 0x00FFFFFF);
                drawContext.fill(stLeft, barTop, stRight, barTop + 1, timeColor);
            }
            if (clampedStack >= 0.99f) {
                int pulse = 40 + (tickCount % 12) * 5;
                int glow = (Math.min(0x7F, pulse) << 24) | (STACK_FULL & 0x00FFFFFF);
                drawContext.fill(stLeft, y - 1, stRight, y, glow);
            }
        }

        if (ready) {
            int pulse = 35 + (tickCount % 10) * 6;
            int glow = (Math.min(0x7F, pulse) << 24) | (COOLDOWN_READY_GLOW & 0x00FFFFFF);
            drawContext.fill(cdLeft, y - 1, cdRight, y, glow);
        }
    }

    private static void drawTrackWell(GuiGraphics g, int left, int right, int top, int bottom) {
        g.fill(left, top, right, bottom, BAR_BACKGROUND);
        g.fill(left, top, right, top + 1, BAR_WELL);
    }

    /** 火焰：冷却 / 推进充能。 */
    private static void drawIconFlame(GuiGraphics g, int x, int y, int color) {
        //  .#
        //  ##
        //  #.
        g.fill(x + 1, y, x + 2, y + 1, color);
        g.fill(x, y + 1, x + 2, y + 2, color);
        g.fill(x, y + 2, x + 1, y + 3, color);
    }

    /** 心形：耐久。 */
    private static void drawIconHeart(GuiGraphics g, int x, int y, int color) {
        //  #.#
        //  ###
        //  .#.
        g.fill(x, y, x + 1, y + 1, color);
        g.fill(x + 2, y, x + 3, y + 1, color);
        g.fill(x, y + 1, x + 3, y + 2, color);
        g.fill(x + 1, y + 2, x + 2, y + 3, color);
    }

    /** 刀锋：破击叠层。 */
    private static void drawIconBlade(GuiGraphics g, int x, int y, int color) {
        //  .#
        //  .#
        //  ##
        g.fill(x + 1, y, x + 2, y + 2, color);
        g.fill(x, y + 2, x + 2, y + 3, color);
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
        // 顶部高光 1px，增加「条」的质感
        if (filled >= 2) {
            int highlight = withAlpha(fillColor, 0x55);
            drawContext.fill(left, bottom - filled, right, bottom - filled + 1, highlight);
        }
    }

    /** 分段竖条：视觉上像叠层格，而不是连续油量。 */
    private static void drawSegmentedMeter(
            GuiGraphics drawContext,
            int left,
            int right,
            int top,
            int bottom,
            float amount,
            int fillColor) {
        float clamped = Math.max(0.0f, Math.min(1.0f, amount));
        int height = bottom - top;
        if (height <= 0) {
            return;
        }

        int filled = Math.max(0, Math.min(height, Math.round(height * clamped)));
        if (filled > 0) {
            drawContext.fill(left, bottom - filled, right, bottom, fillColor);
            if (filled >= 2) {
                drawContext.fill(left, bottom - filled, right, bottom - filled + 1, withAlpha(fillColor, 0x66));
            }
        }

        // 刻度缺口（自下而上均匀）
        for (int i = 1; i < STACK_SEGMENTS; i++) {
            int notchY = bottom - Math.round(height * (i / (float) STACK_SEGMENTS));
            if (notchY > top && notchY < bottom) {
                drawContext.fill(left, notchY, right, notchY + 1, STACK_NOTCH);
            }
        }
    }

    private static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
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
