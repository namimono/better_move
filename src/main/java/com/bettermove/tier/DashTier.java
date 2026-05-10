package com.bettermove.tier;

/**
 * 位移工具的等级。每个等级决定突进的最大距离与耐久度。
 * 冷却时间所有等级共享，参见 {@code DashToolItem.COOLDOWN_TICKS}。
 */
public enum DashTier {
    WOOD("wood", 5.0, 60),
    COPPER("copper", 7.0, 200),
    IRON("iron", 10.0, 350),
    DIAMOND("diamond", 15.0, 1500),
    NETHERITE("netherite", 18.0, 2032);

    private final String id;
    private final double distance;
    private final int durability;

    DashTier(String id, double distance, int durability) {
        this.id = id;
        this.distance = distance;
        this.durability = durability;
    }

    public String getId() {
        return id;
    }

    /** 突进最大距离（方块/格）。 */
    public double getDistance() {
        return distance;
    }

    /** 物品最大耐久。 */
    public int getDurability() {
        return durability;
    }
}
