package com.boostermod.mixin;

import com.boostermod.combat.BoostStrikeSupport;
import com.boostermod.combat.BoostStrikeTargeting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 推进破击：强制/扩大横扫；放宽实体交互距离校验（贴身 + 更长眼距）。
 */
@Mixin(Player.class)
public abstract class PlayerAttackMixin {
    /**
     * 局部变量 index 11 为 canSweep（1.21.1 Player.attack 字节码）。
     * 推进破击 + 剑时始终视为可横扫。
     */
    @ModifyVariable(method = "attack", at = @At("STORE"), index = 11, ordinal = 0)
    private boolean boostermod$forceSweepFlag(boolean canSweep) {
        Player self = (Player) (Object) this;
        if (BoostStrikeSupport.shouldForceSwordSweep(self)) {
            return true;
        }
        return canSweep;
    }

    @ModifyVariable(method = "attack", at = @At("STORE"), index = 11, ordinal = 1)
    private boolean boostermod$forceSweepFlagSecondStore(boolean canSweep) {
        Player self = (Player) (Object) this;
        if (BoostStrikeSupport.shouldForceSwordSweep(self)) {
            return true;
        }
        return canSweep;
    }

    @Redirect(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/AABB;inflate(DDD)Lnet/minecraft/world/phys/AABB;"))
    private AABB boostermod$expandSweepBox(AABB box, double x, double y, double z) {
        Player self = (Player) (Object) this;
        if (BoostStrikeSupport.shouldForceSwordSweep(self)
                && x == 1.0
                && y == 0.25
                && z == 1.0) {
            return box.inflate(
                    BoostStrikeSupport.SWEEP_INFLATE_XZ,
                    BoostStrikeSupport.SWEEP_INFLATE_Y,
                    BoostStrikeSupport.SWEEP_INFLATE_XZ);
        }
        return box.inflate(x, y, z);
    }

    @Redirect(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"))
    private double boostermod$expandSweepDistance(Player player, Entity entity) {
        double distanceSqr = player.distanceToSqr(entity);
        if (BoostStrikeSupport.shouldForceSwordSweep(player)
                && distanceSqr < BoostStrikeSupport.SWEEP_RANGE_SQR
                && distanceSqr >= BoostStrikeSupport.VANILLA_SWEEP_RANGE_SQR) {
            // 落入扩大环带时，伪装成原版 3 格内以通过 distanceToSqr < 9 判定。
            return BoostStrikeSupport.VANILLA_SWEEP_RANGE_SQR - 0.01;
        }
        return distanceSqr;
    }

    /**
     * 服务端校验攻击包时走此方法：推进破击下允许贴身包围盒命中与更长眼距。
     */
    @Inject(
            method = "canInteractWithEntity(Lnet/minecraft/world/phys/AABB;D)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void boostermod$forgivingEntityReach(AABB box, double distance, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (!BoostStrikeSupport.isBoostStrikeWindow(self)) {
            return;
        }
        if (self.getBoundingBox().inflate(BoostStrikeSupport.BODY_HIT_MARGIN).intersects(box)) {
            cir.setReturnValue(true);
            return;
        }
        double range = self.entityInteractionRange() + distance + BoostStrikeSupport.SERVER_REACH_SLACK;
        if (box.distanceToSqr(self.getEyePosition()) < range * range) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "canInteractWithEntity(Lnet/minecraft/world/entity/Entity;D)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void boostermod$forgivingEntityReachEntity(Entity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        if (BoostStrikeTargeting.isWithinForgivingReach(self, living)) {
            cir.setReturnValue(true);
        }
    }
}
