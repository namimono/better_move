package com.boostermod.tier;

/**
 * 推进器护腿的等级。每个等级决定推进的初始冲量、持续推力、推力 tick 数与耐久度。
 *
 * <p>实际飞行距离由 MC 物理引擎（空气阻力 0.91/tick）根据 impulse 与 thrust 自然涌现，
 * 大致符合 <code>d ≈ impulse * 11 + Σ(thrust)</code> 的量级。</p>
 */
public enum BoosterTier {
    WOOD("wood", 0.55, 0.020, 3, 30),
    STONE("stone", 0.65, 0.025, 3, 65),
    COPPER("copper", 0.75, 0.030, 4, 100),
    IRON("iron", 0.90, 0.040, 4, 175),
    GOLD("gold", 1.05, 0.050, 5, 24),
    DIAMOND("diamond", 1.20, 0.060, 5, 750),
    NETHERITE("netherite", 1.40, 0.080, 6, 1016);

    private final String id;
    private final double impulse;
    private final double thrustPerTick;
    private final int thrustTicks;
    private final int durability;

    BoosterTier(
            String id,
            double impulse,
            double thrustPerTick,
            int thrustTicks,
            int durability) {
        this.id = id;
        this.impulse = impulse;
        this.thrustPerTick = thrustPerTick;
        this.thrustTicks = thrustTicks;
        this.durability = durability;
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
}
