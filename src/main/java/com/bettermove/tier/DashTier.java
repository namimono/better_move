package com.bettermove.tier;

/**
 * 位移工具的等级。每个等级决定突进的最大距离、移动速度与耐久度。
 * 冷却时间所有等级共享，参见 {@code DashToolItem.COOLDOWN_TICKS}。
 */
public enum DashTier {
    WOOD("wood", 5.0, 0.65, 60),
    /** 介于木与铜之间，耐久对齐原版石质工具。 */
    STONE("stone", 6.0, 0.75, 131),
    COPPER("copper", 7.0, 0.85, 200),
    IRON("iron", 10.0, 0.95, 350),
    /** 距离略优、耐久很低，贴近原版金质工具定位。 */
    GOLD("gold", 12.0, 1.10, 48),
    DIAMOND("diamond", 15.0, 1.25, 1500),
    NETHERITE("netherite", 18.0, 1.40, 2032);

    private final String id;
    private final double distance;
    private final double speed;
    private final int durability;

    DashTier(String id, double distance, double speed, int durability) {
        this.id = id;
        this.distance = distance;
        this.speed = speed;
        this.durability = durability;
    }

    public String getId() {
        return id;
    }

    /** 突进最大距离（方块/格）。 */
    public double getDistance() {
        return distance;
    }

    /** 冲刺水平速度（格/tick）。 */
    public double getSpeed() {
        return speed;
    }

    /** 物品最大耐久。 */
    public int getDurability() {
        return durability;
    }
}
