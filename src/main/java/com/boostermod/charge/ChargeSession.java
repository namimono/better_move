package com.boostermod.charge;

/**
 * 过载蓄力会话策略：时间窗、倍率、过载标记、取消 / 强制释放 / 迟到释放、资源结算许可。
 * 纯规则模块，无世界副作用。世界侧条件由调用方作为入参传入。
 */
public final class ChargeSession {
    /** 蓄力时长（tick）：3 秒。 */
    public static final int CHARGE_DURATION_TICKS = 60;
    /** 蓄力 + 过载窗口总上限（tick）：5 秒，到达后强制释放。 */
    public static final int MAX_CHARGE_TICKS = 100;
    public static final double MAX_MULTIPLIER = 1.8;

    private Long startTick;

    public boolean isActive() {
        return startTick != null;
    }

    public boolean tryStart(long nowTick) {
        if (isActive()) {
            return false;
        }
        startTick = nowTick;
        return true;
    }

    public void cancel() {
        startTick = null;
    }

    public View view(long nowTick) {
        if (!isActive()) {
            return View.INACTIVE;
        }
        int ticks = chargeTicks(nowTick);
        return new View(true, ticks, multiplierFor(ticks), isOverloaded(ticks), ticks >= MAX_CHARGE_TICKS);
    }

    /**
     * 每 tick 推进。同 tick 取消优先于强制释放。
     */
    public TickResult tick(long nowTick, boolean cancelRequested) {
        if (!isActive()) {
            return TickResult.INACTIVE;
        }
        if (cancelRequested) {
            cancel();
            return TickResult.CANCELLED;
        }
        int ticks = chargeTicks(nowTick);
        return new TickResult(
                true,
                ticks,
                multiplierFor(ticks),
                isOverloaded(ticks),
                ticks >= MAX_CHARGE_TICKS,
                false);
    }

    /**
     * @param canAttemptLaunch 世界侧是否允许尝试起飞（如空中无空中冲刺则为 false）
     */
    public ReleaseResult release(long nowTick, boolean canAttemptLaunch) {
        if (!isActive()) {
            return ReleaseResult.IGNORED;
        }
        int ticks = chargeTicks(nowTick);
        double multiplier = multiplierFor(ticks);
        boolean overloaded = isOverloaded(ticks);
        startTick = null;
        if (!canAttemptLaunch) {
            return new ReleaseResult(true, ticks, multiplier, overloaded, false, false);
        }
        return new ReleaseResult(true, ticks, multiplier, overloaded, true, true);
    }

    public int chargeTicks(long nowTick) {
        if (!isActive()) {
            return 0;
        }
        long elapsed = nowTick - startTick;
        if (elapsed < 0) {
            return 0;
        }
        if (elapsed > MAX_CHARGE_TICKS) {
            return MAX_CHARGE_TICKS;
        }
        return (int) elapsed;
    }

    public static double multiplierFor(int chargeTicks) {
        int clamped = Math.max(0, Math.min(chargeTicks, CHARGE_DURATION_TICKS));
        double chargeSec = clamped / 20.0;
        return 1.0 + 0.8 * Math.min(1.0, Math.max(0.0, chargeSec / 3.0));
    }

    public static boolean isOverloaded(int chargeTicks) {
        return chargeTicks >= CHARGE_DURATION_TICKS;
    }

    public record View(
            boolean active,
            int chargeTicks,
            double multiplier,
            boolean overloaded,
            boolean shouldForceRelease) {
        public static final View INACTIVE = new View(false, 0, 1.0, false, false);
    }

    public record TickResult(
            boolean active,
            int chargeTicks,
            double multiplier,
            boolean overloaded,
            boolean shouldForceRelease,
            boolean cancelled) {
        public static final TickResult INACTIVE = new TickResult(false, 0, 1.0, false, false, false);
        public static final TickResult CANCELLED = new TickResult(false, 0, 1.0, false, false, true);
    }

    public record ReleaseResult(
            boolean accepted,
            int chargeTicks,
            double multiplier,
            boolean overloaded,
            boolean allowLaunchAttempt,
            boolean settleResourcesOnSuccessfulLaunch) {
        public static final ReleaseResult IGNORED = new ReleaseResult(false, 0, 1.0, false, false, false);
    }
}
