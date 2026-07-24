package com.boostermod.client;

import com.boostermod.charge.ChargeSession;
import com.boostermod.item.BoosterEquipment;
import com.boostermod.network.BoosterChargeCancelPayload;
import com.boostermod.network.BoosterChargeStartPayload;
import com.boostermod.network.BoosterRequestPayload;
import com.boostermod.network.BoosterSteerPayload;
import com.boostermod.upgrade.BoosterUpgradeHelper;
import com.boostermod.upgrade.BoosterUpgradeType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

final class BoosterInputHandler {
    private static final int HYPER_WINDOW_TICKS = 3;
    private static final float INPUT_EPSILON = 1.0e-4f;

    private static float lastSentStrafe;
    private static float lastSentForward;
    private static int ticksSinceLandingAfterJump = Integer.MAX_VALUE;
    private static boolean sawJumpArc;
    private static boolean wasOnGround;
    private static boolean wasBoostDown;
    private static boolean localCharging;
    private static int localChargeStartTick;

    private BoosterInputHandler() {}

    static void reset() {
        ticksSinceLandingAfterJump = Integer.MAX_VALUE;
        sawJumpArc = false;
        wasOnGround = false;
        lastSentStrafe = 0.0f;
        lastSentForward = 0.0f;
        wasBoostDown = false;
        clearLocalCharge();
    }

    static boolean isLocalCharging() {
        return localCharging;
    }

    static int localChargeTicks(LocalPlayer player) {
        if (!localCharging) {
            return 0;
        }
        return Math.max(0, Math.min(ChargeSession.MAX_CHARGE_TICKS, player.tickCount - localChargeStartTick));
    }

    /**
     * 必须在 START_CLIENT_TICK 处理推进键：同帧左键攻击在 tick 中段发包，
     * 若推进放在 END，服务端会先收到攻击再收到推进，破击窗口未开、首刀无法必暴。
     */
    static void tickBoostKey(LocalPlayer player, KeyMapping boostKey) {
        Minecraft client = Minecraft.getInstance();
        boolean hasCharge = hasChargeUpgrade(player);

        if (hasCharge) {
            // 排空 click 队列，避免松手后走瞬发旁路。
            while (boostKey.consumeClick()) {
                // drain
            }

            if (client.screen != null) {
                if (localCharging || wasBoostDown) {
                    cancelLocalCharge();
                }
                wasBoostDown = false;
                return;
            }

            boolean down = boostKey.isDown();
            if (localCharging && localChargeTicks(player) >= ChargeSession.MAX_CHARGE_TICKS) {
                // 服务端将强制释放；清本地蓄力，松手不再补发开火包。
                clearLocalCharge();
                BoostStrikeClientState.onBoostRequest();
            }
            if (down && !wasBoostDown) {
                if (canSoftStartCharge(player)) {
                    beginLocalCharge(player);
                }
            } else if (!down && wasBoostDown) {
                if (localCharging) {
                    fireChargeRelease(player);
                }
            }
            wasBoostDown = down;
            return;
        }

        // 无过载蓄力：保留点按瞬发。
        if (localCharging) {
            clearLocalCharge();
        }
        wasBoostDown = false;
        while (boostKey.consumeClick()) {
            double[] boostDirection = horizontalInputVector(player);
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

    private static void beginLocalCharge(LocalPlayer player) {
        localCharging = true;
        localChargeStartTick = player.tickCount;
        ClientPlayNetworking.send(BoosterChargeStartPayload.INSTANCE);
    }

    private static void fireChargeRelease(LocalPlayer player) {
        clearLocalCharge();
        double[] boostDirection = horizontalInputVector(player);
        BoostStrikeClientState.onBoostRequest();
        ClientPlayNetworking.send(new BoosterRequestPayload(
                boostDirection[0],
                boostDirection[1],
                -1,
                landingTicksAgoForPayload()));
    }

    private static void cancelLocalCharge() {
        if (localCharging) {
            ClientPlayNetworking.send(BoosterChargeCancelPayload.INSTANCE);
        }
        clearLocalCharge();
    }

    private static void clearLocalCharge() {
        localCharging = false;
        localChargeStartTick = 0;
    }

    private static boolean hasChargeUpgrade(LocalPlayer player) {
        return BoosterEquipment.find(player)
                .map(equipped -> BoosterUpgradeHelper.hasUpgrade(
                        equipped.stack(), BoosterUpgradeType.CHARGE, player.registryAccess()))
                .orElse(false);
    }

    private static boolean canSoftStartCharge(LocalPlayer player) {
        if (player.isSpectator() || !player.isAlive() || player.isSleeping()) {
            return false;
        }
        BoosterEquipment.Equipped equipped = BoosterEquipment.find(player).orElse(null);
        if (equipped == null) {
            return false;
        }
        var registries = player.registryAccess();
        if (!BoosterUpgradeHelper.hasUpgrade(equipped.stack(), BoosterUpgradeType.CHARGE, registries)) {
            return false;
        }
        boolean noCooldown = BoosterUpgradeHelper.hasUpgrade(
                equipped.stack(), BoosterUpgradeType.NO_COOLDOWN, registries);
        if (!noCooldown && player.getCooldowns().isOnCooldown(equipped.item())) {
            return false;
        }
        return player.getAbilities().instabuild || player.getFoodData().getFoodLevel() >= 6;
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
