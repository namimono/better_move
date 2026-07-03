package com.boostermod.feature;

public enum BoosterFeature {
    BURROW("burrow"),
    VERTICAL_LAUNCH("vertical_launch"),
    NO_COOLDOWN("no_cooldown"),
    RANDOM_IMPULSE("random_impulse");

    private final String id;

    BoosterFeature(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static BoosterFeature byId(String id) {
        for (BoosterFeature feature : values()) {
            if (feature.id.equals(id)) {
                return feature;
            }
        }
        return null;
    }
}
