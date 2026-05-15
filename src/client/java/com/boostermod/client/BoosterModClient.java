package com.boostermod.client;

import com.boostermod.network.BoosterRequestPayload;
import com.boostermod.network.BoosterSteerPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

public class BoosterModClient implements ClientModInitializer {
    private static final int HYPER_WINDOW_TICKS = 3;
    public static KeyMapping boostKey;

    /** 上一次发送给服务端的 strafe 输入，用于去重，避免 0 状态下持续刷包。 */
    private static float lastSentStrafe;
    /** 上一次发送给服务端的 forward 输入，用于去重。 */
    private static float lastSentForward;
    private static int ticksSinceLandingAfterJump = Integer.MAX_VALUE;
    private static boolean sawJumpArc;
    private static boolean wasOnGround;

    @Override
    public void onInitializeClient() {
        boostKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.boostermod.boost",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                "key.categories.boostermod"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            if (player == null) {
                resetHyperTracking();
                return;
            }
            trackHyperWindows(client, player);
            while (boostKey.consumeClick()) {
                ClientPlayNetworking.send(new BoosterRequestPayload(
                        intendedBoostDirX(player),
                        intendedBoostDirZ(player),
                        -1,
                        landingTicksAgoForPayload()));
            }
            syncSteerInput(player);
        });
    }

    private static void trackHyperWindows(net.minecraft.client.Minecraft client, LocalPlayer player) {
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

    private static void resetHyperTracking() {
        ticksSinceLandingAfterJump = Integer.MAX_VALUE;
        sawJumpArc = false;
        wasOnGround = false;
    }

    /**
     * 把当前移动键状态同步给服务端，用于推进期间方向微调。客户端并不知道服务端"是否正在推进"，
     * 所以策略是：只在输入相对上次发送有变化时发包，并在归零时再发一次"清零"，最大节流到
     * "玩家每按下/抬起一次键就一两个包"。
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

    private static double intendedBoostDirX(LocalPlayer player) {
        return horizontalInputVector(player)[0];
    }

    private static double intendedBoostDirZ(LocalPlayer player) {
        return horizontalInputVector(player)[1];
    }

    private static double[] horizontalInputVector(LocalPlayer player) {
        float forward = player.input.forwardImpulse;
        float left = player.input.leftImpulse;
        if (Math.abs(forward) < 1.0e-4f && Math.abs(left) < 1.0e-4f) {
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
