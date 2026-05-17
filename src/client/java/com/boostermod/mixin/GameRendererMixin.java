package com.boostermod.mixin;

import com.boostermod.client.BoosterFeedbackEffects;
import com.mojang.blaze3d.vertex.PoseStack;
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
    }
}
