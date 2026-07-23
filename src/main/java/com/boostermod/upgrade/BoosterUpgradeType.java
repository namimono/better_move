package com.boostermod.upgrade;

public enum BoosterUpgradeType {
    AIR_DASH("air_dash_upgrade"),
    BURROW("burrow_upgrade"),
    VERTICAL_LAUNCH("vertical_launch_upgrade"),
    NO_COOLDOWN("no_cooldown_upgrade"),
    RANDOM_IMPULSE("random_impulse_upgrade"),
    /** 推进破击：推进中主动近战命中才触发，非碰撞冲撞。 */
    BOOST_STRIKE("boost_strike_upgrade");

    private final String itemId;

    BoosterUpgradeType(String itemId) {
        this.itemId = itemId;
    }

    public String getItemId() {
        return itemId;
    }

    public String getTooltipKey() {
        return "item.boostermod." + itemId + ".tooltip";
    }
}
