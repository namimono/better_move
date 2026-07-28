package com.boostermod.villager;

import java.util.Locale;
import java.util.Optional;

/**
 * 武装村民锁敌目标类型。
 */
public enum ArmedVillagerTargetMode {
    /** 可攻击的生存/冒险玩家（默认）。 */
    PLAYERS("players"),
    /** 敌对怪物（{@code Monster}）。 */
    MONSTERS("monsters");

    private final String id;

    ArmedVillagerTargetMode(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static Optional<ArmedVillagerTargetMode> byId(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (ArmedVillagerTargetMode mode : values()) {
            if (mode.id.equals(normalized) || mode.name().equalsIgnoreCase(normalized)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}
