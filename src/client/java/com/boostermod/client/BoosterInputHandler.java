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

    /**
     * 必须在 START_CLIENT_TICK 处理推进键：同帧左键攻击在 tick 中段发包，
     * 若推进放在 END，服务端会先收到攻击再收到推进，破击窗口未开、首刀无法必暴。
     */
    static void tickBoostKey(LocalPlayer player, KeyMapping boostKey) {
        while (boostKey.consumeClick()) {
            double[] boostDirection = horizontalInputVector(player);
            // 立刻开客户端破击窗口（触及/辅助锁定），不等 S2C。
            BoostStrikeClientState.onBoostRequest();
            ClientPlayNetworking.send(new BoosterRequestPayload(
                    boostDirection[0],
                    boostDirection[1],
                    -1,
                    landingTicksAgoForPayload()));
        }
    }

    static void tickEnd(LocalPlayer player) {
        trackHyperWindows(player);
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
