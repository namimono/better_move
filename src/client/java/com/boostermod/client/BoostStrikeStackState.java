package com.boostermod.client;

/**
 * 客户端推进破击叠层快照：由 S2C 写入，本地按 tick 递减剩余时间，供 HUD 读取。
 */
public final class BoostStrikeStackState {
    private static float stackAmount;
    private static float maxStack;
    private static int remainingTicks;
    /** 收包时用于寿命条比例的基准持续（刷新时抬高，心跳不缩小）。 */
    private static int durationTicks;

    private BoostStrikeStackState() {}

    public static void apply(float stack, float max, int remaining) {
        float prevStack = stackAmount;
        if (stack <= 0.0f || max <= 0.0f || remaining <= 0) {
            clear();
            return;
        }

        stackAmount = stack;
        maxStack = max;
        remainingTicks = remaining;

        if (stack > prevStack || remaining > durationTicks || durationTicks <= 0) {
            durationTicks = remaining;
        }
    }

    public static void tick() {
        if (remainingTicks <= 0) {
            if (stackAmount > 0.0f) {
                clear();
            }
            return;
        }
        remainingTicks--;
        if (remainingTicks <= 0) {
            clear();
        }
    }

    public static void reset() {
        clear();
    }

    private static void clear() {
        stackAmount = 0.0f;
        maxStack = 0.0f;
        remainingTicks = 0;
        durationTicks = 0;
    }

    public static float stackRatio() {
        if (maxStack <= 0.0f || stackAmount <= 0.0f) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, stackAmount / maxStack));
    }

    public static float timeRatio() {
        if (durationTicks <= 0 || remainingTicks <= 0) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, remainingTicks / (float) durationTicks));
    }

    public static boolean hasActiveStack() {
        return stackAmount > 0.0f && maxStack > 0.0f && remainingTicks > 0;
    }
}
