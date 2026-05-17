package com.boostermod.tier;

/**
 * 推进器护腿的等级。每个等级决定推进的初始冲量、持续推力、推力 tick 数与耐久度。
 *
 * <p>实际飞行距离由 MC 物理引擎（空气阻力 0.91/tick）根据 impulse 与 thrust 自然涌现，
 * 大致符合 <code>d ≈ impulse * 11 + Σ(thrust)</code> 的量级。</p>
 */
public enum BoosterTier {
    WOOD("wood", 0.55, 0.020, 10, 30, 0),
    STONE("stone", 0.65, 0.025, 10, 65, 0),
    COPPER("copper", 0.75, 0.030, 10, 100, 2),
    IRON("iron", 0.90, 0.040, 10, 175, 3),
    GOLD("gold", 1.05, 0.050, 10, 24, 4),
    DIAMOND("diamond", 1.20, 0.060, 10, 750, 5),
    NETHERITE("netherite", 1.40, 0.080, 10, 1016, 6);

    private final String id;
    private final double impulse;
    private final double thrustPerTick;
    private final int thrustTicks;
    private final int durability;
    private final int upgradeSlots;

    BoosterTier(
            String id,
            double impulse,
            double thrustPerTick,
            int thrustTicks,
            int durability,
            int upgradeSlots) {
        this.id = id;
        this.impulse = impulse;
        this.thrustPerTick = thrustPerTick;
        this.thrustTicks = thrustTicks;
        this.durability = durability;
        this.upgradeSlots = upgradeSlots;
    }

    public String getId() {
        return id;
    }

    public double getDefaultImpulse() {
        return impulse;
    }

    public double getDefaultThrustPerTick() {
        return thrustPerTick;
    }

    public int getDefaultThrustTicks() {
        return thrustTicks;
    }

    public int getDurability() {
        return durability;
    }

    public int getUpgradeSlots() {
        return upgradeSlots;
    }
}
