package com.boostermod.client;

public final class BoosterShakeState {
    private static boolean enabled = true;

    private BoosterShakeState() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        BoosterShakeState.enabled = enabled;
    }

    public static void reset() {
        enabled = true;
    }
}
