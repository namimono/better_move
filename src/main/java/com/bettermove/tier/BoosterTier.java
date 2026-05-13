package com.bettermove.tier;

/**
 * 推进器护腿的等级。每个等级决定推进的最大距离、巡航速度、喷射曲线参数与耐久度。
 * 冷却时间所有等级共享，参见 {@code BoosterLeggingsItem.COOLDOWN_TICKS}。
 */
public enum BoosterTier {
    WOOD("wood", 5.0, 0.65, 1.08, 0.42, 60),
    STONE("stone", 6.0, 0.75, 1.10, 0.40, 131),
    COPPER("copper", 7.0, 0.85, 1.12, 0.38, 200),
    IRON("iron", 10.0, 0.95, 1.14, 0.36, 350),
    GOLD("gold", 12.0, 1.10, 1.17, 0.34, 48),
    DIAMOND("diamond", 15.0, 1.25, 1.20, 0.30, 1500),
    NETHERITE("netherite", 18.0, 1.40, 1.24, 0.26, 2032);

    private final String id;
    private final double distance;
    private final double speed;
    private final double boostStrength;
    private final double endSpeedMultiplier;
    private final int durability;

    BoosterTier(
            String id,
            double distance,
            double speed,
            double boostStrength,
            double endSpeedMultiplier,
            int durability) {
        this.id = id;
        this.distance = distance;
        this.speed = speed;
        this.boostStrength = boostStrength;
        this.endSpeedMultiplier = endSpeedMultiplier;
        this.durability = durability;
    }

    public String getId() {
        return id;
    }

    /** 推进最大距离（方块/格）。 */
    public double getDistance() {
        return distance;
    }

    /** 推进巡航速度（格/tick）。 */
    public double getSpeed() {
        return speed;
    }

    /** 喷射曲线峰值倍率，决定点火阶段的爆发强度。 */
    public double getBoostStrength() {
        return boostStrength;
    }

    /** 末段最小速度倍率，越低越像推进器断推。 */
    public double getEndSpeedMultiplier() {
        return endSpeedMultiplier;
    }

    /** 物品最大耐久。 */
    public int getDurability() {
        return durability;
    }
}
