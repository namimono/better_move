package com.boostermod.mixin;

import com.boostermod.villager.ArmedVillagerCombat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerArmedCombatMixin {
    @Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
    private void boostermod$suppressBrainWhileEngaged(CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;
        if (ArmedVillagerCombat.tickEngagement(villager)) {
            ci.cancel();
        }
    }

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void boostermod$blockTradingWhileEngaged(
            Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Villager villager = (Villager) (Object) this;
        if (ArmedVillagerCombat.isEngaged(villager)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
