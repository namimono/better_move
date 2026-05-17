package com.boostermod.client;

import com.boostermod.network.BoosterRequestPayload;
import com.boostermod.network.BoosterSteerPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;

final class BoosterInputHandler {
    private static final int HYPER_WINDOW_TICKS = 3;
    private static final float INPUT_EPSILON = 1.0e-4f;

    private static float lastSentStrafe;
    private static float lastSentForward;
    private static int ticksSinceLandingAfterJump = Integer.MAX_VALUE;
    private static boolean sawJumpArc;
    private static boolean wasOnGround;

    private BoosterInputHandler() {}

    static void reset() {
        ticksSinceLandingAfterJump = Integer.MAX_VALUE;
        sawJumpArc = false;
        wasOnGround = false;
        lastSentStrafe = 0.0f;
        lastSentForward = 0.0f;
    }

    static void tick(LocalPlayer player, KeyMapping boostKey) {
        trackHyperWindows(player);

        while (boostKey.consumeClick()) {
            double[] boostDirection = horizontalInputVector(player);
            ClientPlayNetworking.send(new BoosterRequestPayload(
                    boostDirection[0],
                    boostDirection[1],
                    -1,
                    landingTicksAgoForPayload()));
        }

        syncSteerInput(player);
    }

    private static void trackHyperWindows(LocalPlayer player) {
        boolean onGround = player.onGround();
        if (!onGround && player.getDeltaMovement().y > 0.0) {
            sawJumpArc = true;
        }
        if (sawJumpArc && onGround && !wasOnGround) {
            ticksSinceLandingAfterJump = 0;
            sawJumpArc = false;
        }
        if (sawJumpArc && onGround) {
            sawJumpArc = false;
        }
        if (ticksSinceLandingAfterJump != Integer.MAX_VALUE) {
            ticksSinceLandingAfterJump++;
        }

        wasOnGround = onGround;
    }

    private static int landingTicksAgoForPayload() {
        return ticksSinceLandingAfterJump <= HYPER_WINDOW_TICKS ? ticksSinceLandingAfterJump : -1;
    }

    /**
     * Sync local movement input to the server so steering corrections stay responsive while boosting.
     * Only send updates when the values change to avoid spamming packets every tick.
     */
    private static void syncSteerInput(LocalPlayer player) {
        float strafe = player.input.leftImpulse;
        float forward = player.input.forwardImpulse;
        if (strafe == lastSentStrafe && forward == lastSentForward) {
            return;
        }

        lastSentStrafe = strafe;
        lastSentForward = forward;
        ClientPlayNetworking.send(new BoosterSteerPayload(strafe, forward));
    }

    private static double[] horizontalInputVector(LocalPlayer player) {
        float forward = player.input.forwardImpulse;
        float left = player.input.leftImpulse;
        if (Math.abs(forward) < INPUT_EPSILON && Math.abs(left) < INPUT_EPSILON) {
            return new double[] {0.0, 0.0};
        }

        double yawRad = Math.toRadians(player.getYRot());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        double x = left * cos - forward * sin;
        double z = forward * cos + left * sin;
        return new double[] {x, z};
    }
}
