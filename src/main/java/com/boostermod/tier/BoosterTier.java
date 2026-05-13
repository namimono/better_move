package com.boostermod.tier;

/**
 * 推进器护腿的等级。每个等级决定推进的最大距离、巡航速度、喷射曲线参数与耐久度。
 */
public enum BoosterTier {
    WOOD("wood", 5.0, 0.65, 1.08, 0.42, 30),
    STONE("stone", 6.0, 0.75, 1.10, 0.40, 65),
    COPPER("copper", 7.0, 0.85, 1.12, 0.38, 100),
    IRON("iron", 10.0, 0.95, 1.14, 0.36, 175),
    GOLD("gold", 12.0, 1.10, 1.17, 0.34, 24),
    DIAMOND("diamond", 15.0, 1.25, 1.20, 0.30, 750),
    NETHERITE("netherite", 18.0, 1.40, 1.24, 0.26, 1016);

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

    public double getDistance() {
        return distance;
    }

    public double getSpeed() {
        return speed;
    }

    public double getBoostStrength() {
        return boostStrength;
    }

    public double getEndSpeedMultiplier() {
        return endSpeedMultiplier;
    }

    public int getDurability() {
        return durability;
    }
}
