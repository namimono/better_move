package com.boostermod.client;

public final class BoosterHudState {
    private static boolean enabled = true;

    private BoosterHudState() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        BoosterHudState.enabled = enabled;
    }

    public static void reset() {
        enabled = true;
    }
}
