package com.boostermod.mixin;

import com.boostermod.client.BoosterFeedbackEffects;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void boostermod$applyBoostFovModifier(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(BoosterFeedbackEffects.applyFovModifier(cir.getReturnValue()));
    }
}
