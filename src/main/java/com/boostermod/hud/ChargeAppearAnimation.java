package com.boostermod.hud;

/**
 * 蓄力轨出场/退场动效：按游戏 tick 推进，避免 render 帧率把动画压成硬切。
 * <p>
 * Spec P0：可感知的淡入淡出和/或缩放；禁止硬切出现/消失。
 */
public final class ChargeAppearAnimation {
    /** ~0.4s @ 20 tps — 可感知，又不会拖到松手后还挂很久。 */
    public static final int FADE_TICKS = 8;
    public static final float RENDER_EPSILON = 0.01f;
    /** 缩放出场下限（appear=0）；appear=1 时为满高。 */
    public static final float SCALE_MIN = 0.4f;

    private ChargeAppearAnimation() {}

    /** 推进一个游戏 tick。 */
    public static float step(float current, boolean visible) {
        float target = visible ? 1.0f : 0.0f;
        float delta = 1.0f / FADE_TICKS;
        if (current < target) {
            return Math.min(target, current + delta);
        }
        if (current > target) {
            return Math.max(target, current - delta);
        }
        return current;
    }

    /**
     * 仅在 {@code gameTick} 相对 {@code lastAdvancedTick} 变化时推进一次，
     * 防止 HUD 每帧 render 把 8 tick 动画跑成 8 帧。
     */
    public static float stepOnTick(float current, boolean visible, int gameTick, int lastAdvancedTick) {
        if (gameTick == lastAdvancedTick) {
            return current;
        }
        return step(current, visible);
    }

    public static int alpha(float appear) {
        return Math.round(0xFF * clamp01(appear));
    }

    public static float heightScale(float appear) {
        float a = clamp01(appear);
        return SCALE_MIN + (1.0f - SCALE_MIN) * a;
    }

    public static boolean shouldRender(float appear) {
        return appear > RENDER_EPSILON;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
