package com.boostermod.combat;

import com.boostermod.tier.BoosterTier;

/**
 * 推进破击分档数值。持续时间已相对初稿整体 +30%。
 * 命中/击杀攻击加成为<strong>叠层增量</strong>（可叠加，受 {@link #maxStackBonus} 上限约束）。
 */
public record BoostStrikeProfile(
        float bonusDamage,
        double hitAttackBonus,
        int hitDurationTicks,
        double killAttackBonus,
        int killDurationTicks,
        double maxStackBonus) {

    /** 命中 buff 持续：60 × 1.3 */
    private static final int HIT_DURATION_TICKS = 78;
    /** 击杀 buff 持续（铜～金）：100 × 1.3 */
    private static final int KILL_DURATION_TICKS_LOW = 130;
    /** 击杀 buff 持续（钻石～下界合金）：120 × 1.3 */
    private static final int KILL_DURATION_TICKS_HIGH = 156;

    public static BoostStrikeProfile forTier(BoosterTier tier) {
        return switch (tier) {
            case WOOD, STONE, COPPER -> new BoostStrikeProfile(
                    2.0f, 1.0, HIT_DURATION_TICKS, 2.0, KILL_DURATION_TICKS_LOW, 24.0);
            case IRON -> new BoostStrikeProfile(
                    3.0f, 1.5, HIT_DURATION_TICKS, 3.0, KILL_DURATION_TICKS_LOW, 28.0);
            case GOLD -> new BoostStrikeProfile(
                    4.0f, 2.0, HIT_DURATION_TICKS, 4.0, KILL_DURATION_TICKS_LOW, 32.0);
            case DIAMOND -> new BoostStrikeProfile(
                    6.0f, 3.0, HIT_DURATION_TICKS, 5.0, KILL_DURATION_TICKS_HIGH, 36.0);
            case NETHERITE -> new BoostStrikeProfile(
                    8.0f, 4.0, HIT_DURATION_TICKS, 7.0, KILL_DURATION_TICKS_HIGH, 40.0);
        };
    }

    /** 一次有效命中叠层增量（未击杀仅此项）。 */
    public double stackDeltaOnHit() {
        return hitAttackBonus;
    }

    /**
     * 一次击杀叠层增量 = 命中增量 + 击杀增量（击杀本身也是一次命中）。
     */
    public double stackDeltaOnKill() {
        return hitAttackBonus + killAttackBonus;
    }
}
