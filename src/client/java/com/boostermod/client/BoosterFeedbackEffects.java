package com.boostermod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;

public final class BoosterFeedbackEffects {
    private static final int NORMAL_DURATION_TICKS = 8;
    private static final int HYPER_DURATION_TICKS = 11;
    private static final float NORMAL_FOV_MODIFIER_BONUS = 0.18f;
    private static final float HYPER_FOV_MODIFIER_BONUS = 0.35f;
    private static final float FOV_MODIFIER_DECAY = 0.80f;
    private static final float FOV_MODIFIER_MIN_VISIBLE = 0.002f;

    private static int ticksLeft;
    private static int durationTicks;
    private static boolean hyper;
    private static float fovModifierBonus;

    private BoosterFeedbackEffects() {}

    public static void trigger(boolean hyperBoost) {
        hyper = hyperBoost;
        durationTicks = hyper ? HYPER_DURATION_TICKS : NORMAL_DURATION_TICKS;
        ticksLeft = durationTicks;
        fovModifierBonus = Math.max(
                fovModifierBonus,
                hyper ? HYPER_FOV_MODIFIER_BONUS : NORMAL_FOV_MODIFIER_BONUS);

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        if (hyper) {
            client.player.playSound(SoundEvents.FIRECHARGE_USE, 0.70f, 1.65f);
            client.player.playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.35f, 0.85f);
        }
    }

    public static void tick() {
        fovModifierBonus *= FOV_MODIFIER_DECAY;
        if (fovModifierBonus < FOV_MODIFIER_MIN_VISIBLE) {
            fovModifierBonus = 0.0f;
        }

        if (ticksLeft > 0) {
            ticksLeft--;
        }
    }

    public static float applyFovModifier(float modifier) {
        if (fovModifierBonus <= 0.0f) {
            return modifier;
        }

        return modifier + fovModifierBonus;
    }

    public static void applyCameraKick(PoseStack poseStack, float tickDelta) {
        if (!BoosterShakeState.isEnabled()) {
            return;
        }

        float pulse = pulse(tickDelta);
        if (pulse <= 0.0f) {
            return;
        }

        float phase = (durationTicks - ticksLeft + tickDelta) * (hyper ? 2.2f : 1.8f);
        float yaw = (float) Math.sin(phase * 1.7f) * pulse * (hyper ? 0.34f : 0.10f);
        float roll = (float) Math.sin(phase) * pulse * (hyper ? 0.52f : 0.16f);
        float bob = (float) Math.cos(phase * 1.3f) * pulse * (hyper ? 0.009f : 0.003f);

        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
        poseStack.translate(0.0f, bob, 0.0f);
    }

    private static float pulse() {
        return pulse(1.0f);
    }

    private static float pulse(float tickDelta) {
        if (ticksLeft <= 0 || durationTicks <= 0) {
            return 0.0f;
        }

        float age = durationTicks - ticksLeft + tickDelta;
        float progress = Math.min(1.0f, Math.max(0.0f, age / durationTicks));
        return (float) Math.pow(1.0f - progress, hyper ? 1.35f : 1.55f);
    }

}
