package com.boostermod.mixin;

import com.boostermod.client.BoosterFeedbackEffects;
import com.boostermod.client.BoosterShakeState;
import com.boostermod.client.BoosterStrikeFeedbackEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "bobView", at = @At("TAIL"))
    private void boostermod$applyBoostCameraKick(PoseStack poseStack, float tickDelta, CallbackInfo ci) {
        BoosterFeedbackEffects.applyCameraKick(poseStack, tickDelta);
        applyStrikeCameraKick(poseStack, tickDelta);
    }

    private static void applyStrikeCameraKick(PoseStack poseStack, float tickDelta) {
        if (!BoosterShakeState.isEnabled()) {
            return;
        }
        float pulse = BoosterStrikeFeedbackEffects.pulse(tickDelta);
        if (pulse <= 0.0f) {
            return;
        }
        boolean kill = BoosterStrikeFeedbackEffects.isKill();
        float phase = pulse * (kill ? 3.2f : 2.4f);
        float yaw = (float) Math.sin(phase * 2.1f) * pulse * (kill ? 0.28f : 0.14f);
        float roll = (float) Math.sin(phase * 1.4f) * pulse * (kill ? 0.40f : 0.18f);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
    }
}
