package com.boostermod.mixin;

import com.boostermod.combat.BoostStrikeSupport;
import com.boostermod.combat.BoostStrikeTargeting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 推进破击：准星未锁到生物（MISS/方块/身后）时，用贴身+视角锥辅助锁定最近目标并发起攻击。
 */
@Mixin(Minecraft.class)
public abstract class MinecraftAttackMixin {
    @Shadow
    public LocalPlayer player;

    @Shadow
    public MultiPlayerGameMode gameMode;

    @Shadow
    public HitResult hitResult;

    @Shadow
    private int missTime;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void boostermod$assistBoostStrikeAttack(CallbackInfoReturnable<Boolean> cir) {
        if (this.missTime > 0) {
            return;
        }
        LocalPlayer localPlayer = this.player;
        MultiPlayerGameMode mode = this.gameMode;
        if (localPlayer == null || mode == null || localPlayer.isHandsBusy()) {
            return;
        }
        if (!BoostStrikeSupport.isBoostStrikeWindow(localPlayer)) {
            return;
        }

        HitResult hit = this.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
            Entity crosshair = ((EntityHitResult) hit).getEntity();
            if (crosshair instanceof LivingEntity living && localPlayer.canAttack(living)) {
                // 准星已锁合法生物：走原版路径（服务端仍有宽容校验）。
                return;
            }
        }

        LivingEntity assist = BoostStrikeTargeting.findAssistTarget(localPlayer);
        if (assist == null) {
            return;
        }

        mode.attack(localPlayer, assist);
        localPlayer.swing(InteractionHand.MAIN_HAND);
        if (mode.hasMissTime()) {
            this.missTime = 10;
        }
        cir.setReturnValue(true);
    }
}
