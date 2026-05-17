package com.boostermod.client;

import com.boostermod.item.BoosterLeggingsItem;
import com.boostermod.network.BoosterRequestPayload;
import com.boostermod.network.BoosterSteerPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class BoosterModClient implements ClientModInitializer {
    private static final int HYPER_WINDOW_TICKS = 3;
    private static final float READY_SOUND_VOLUME = 0.2f;
    private static final float READY_SOUND_PITCH = 1.8f;
    private static final float HYPER_IGNITION_SOUND_VOLUME = 1.05f;
    private static final float HYPER_IGNITION_SOUND_PITCH = 1.65f;
    private static final float HYPER_SURGE_SOUND_VOLUME = 0.65f;
    private static final float HYPER_SURGE_SOUND_PITCH = 1.35f;

    public static KeyMapping boostKey;

    private static float lastSentStrafe;
    private static float lastSentForward;
    private static int ticksSinceLandingAfterJump = Integer.MAX_VALUE;
    private static boolean sawJumpArc;
    private static boolean wasOnGround;
    private static boolean hadBoosterCooldown;

    @Override
    public void onInitializeClient() {
        boostKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.boostermod.boost",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                "key.categories.boostermod"
        ));

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> renderBoosterCooldownIndicator(drawContext));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            if (player == null) {
                resetHyperTracking();
                hadBoosterCooldown = false;
                return;
            }

            trackHyperWindows(player);
            tickReadySound(player);

            while (boostKey.consumeClick()) {
                int landingTicksAgo = landingTicksAgoForPayload();
                ClientPlayNetworking.send(new BoosterRequestPayload(
                        intendedBoostDirX(player),
                        intendedBoostDirZ(player),
                        -1,
                        landingTicksAgo));
                if (landingTicksAgo >= 0 && canPlayLocalHyperSound(player)) {
                    playLocalHyperSound(player);
                }
            }

            syncSteerInput(player);
        });
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

    private static void resetHyperTracking() {
        ticksSinceLandingAfterJump = Integer.MAX_VALUE;
        sawJumpArc = false;
        wasOnGround = false;
    }

    private static void tickReadySound(LocalPlayer player) {
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        if (!(legs.getItem() instanceof BoosterLeggingsItem boosterItem)) {
            hadBoosterCooldown = false;
            return;
        }

        boolean hasCooldown = player.getCooldowns().isOnCooldown(boosterItem);
        if (hadBoosterCooldown && !hasCooldown) {
            player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, READY_SOUND_VOLUME, READY_SOUND_PITCH);
        }
        hadBoosterCooldown = hasCooldown;
    }

    private static boolean canPlayLocalHyperSound(LocalPlayer player) {
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        return legs.getItem() instanceof BoosterLeggingsItem boosterItem
                && !player.getCooldowns().isOnCooldown(boosterItem);
    }

    private static void playLocalHyperSound(LocalPlayer player) {
        player.playSound(
                SoundEvents.FIRECHARGE_USE,
                HYPER_IGNITION_SOUND_VOLUME,
                HYPER_IGNITION_SOUND_PITCH);
        player.playSound(
                SoundEvents.TRIDENT_RIPTIDE_1.value(),
                HYPER_SURGE_SOUND_VOLUME,
                HYPER_SURGE_SOUND_PITCH);
    }

    private static void renderBoosterCooldownIndicator(GuiGraphics drawContext) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.options.hideGui) {
            return;
        }

        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        Item item = legs.getItem();
        if (!(item instanceof BoosterLeggingsItem)) {
            return;
        }

        float cooldownRemaining = player.getCooldowns().getCooldownPercent(item, 0.0f);
        boolean ready = cooldownRemaining <= 0.0f;
        if (!ready && cooldownRemaining >= 1.0f) {
            return;
        }

        float charge = ready ? 1.0f : 1.0f - cooldownRemaining;
        float durability = durabilityPercent(legs);
        BoosterHudRenderer.render(drawContext, charge, ready, durability, player.tickCount);
    }

    private static float durabilityPercent(ItemStack stack) {
        if (!stack.isDamageableItem()) {
            return 1.0f;
        }

        int maxDamage = stack.getMaxDamage();
        if (maxDamage <= 0) {
            return 1.0f;
        }

        return Math.max(0.0f, (maxDamage - stack.getDamageValue()) / (float) maxDamage);
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
