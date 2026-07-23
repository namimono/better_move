package com.boostermod.mixin;

import com.boostermod.combat.BoostStrikeSupport;
import com.boostermod.combat.BoostStrikeTargeting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 推进破击：窗口内满伤+必暴击、强制/扩大横扫；放宽实体交互距离校验。
 */
@Mixin(Player.class)
public abstract class PlayerAttackMixin {
    @Unique
    private boolean boostermod$spoofedForCrit;
    @Unique
    private boolean boostermod$savedSprinting;
    @Unique
    private boolean boostermod$savedOnGround;
    @Unique
    private float boostermod$savedFallDistance;

    /**
     * 破击窗口内：把攻击冷却视作充满，连点/推进瞬间也不会因半蓄力变成弱击。
     * （原版只在 attack 开头读一次 scale，此处唯一调用点。）
     */
    @Redirect(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getAttackStrengthScale(F)F"))
    private float boostermod$fullStrengthInStrikeWindow(Player player, float partialTicks) {
        float scale = player.getAttackStrengthScale(partialTicks);
        if (BoostStrikeSupport.shouldForceCritical(player)) {
            return 1.0f;
        }
        return scale;
    }

    /**
     * 破击窗口内伪装原版暴击前置条件：离地、有下落距离、非疾跑。
     * 推进推力期每 tick 会 resetFallDistance，仅改 critical 局部变量不够稳时，用此路径保证 ×1.5 与暴击特效。
     */
    @Inject(method = "attack", at = @At("HEAD"))
    private void boostermod$spoofVanillaCritConditions(Entity target, CallbackInfo ci) {
        boostermod$spoofedForCrit = false;
        if (!(target instanceof LivingEntity)) {
            return;
        }
        Player self = (Player) (Object) this;
        if (!BoostStrikeSupport.shouldForceCritical(self)) {
            return;
        }

        boostermod$savedSprinting = self.isSprinting();
        boostermod$savedOnGround = self.onGround();
        boostermod$savedFallDistance = self.fallDistance;

        self.setSprinting(false);
        self.setOnGround(false);
        if (self.fallDistance <= 0.0f) {
            self.fallDistance = 1.0f;
        }
        boostermod$spoofedForCrit = true;
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void boostermod$restoreAfterCritSpoof(Entity target, CallbackInfo ci) {
        if (!boostermod$spoofedForCrit) {
            return;
        }
        Player self = (Player) (Object) this;
        self.setSprinting(boostermod$savedSprinting);
        self.setOnGround(boostermod$savedOnGround);
        self.fallDistance = boostermod$savedFallDistance;
        boostermod$spoofedForCrit = false;
    }

    /**
     * 局部变量 index 9 为 critical（1.21.1）。双保险：即使伪装条件未生效也强制暴击标志。
     */
    @ModifyVariable(method = "attack", at = @At("STORE"), index = 9, ordinal = 0)
    private boolean boostermod$forceCriticalFlag(boolean critical, Entity target) {
        if (critical) {
            return true;
        }
        if (!(target instanceof LivingEntity)) {
            return false;
        }
        return BoostStrikeSupport.shouldForceCritical((Player) (Object) this);
    }

    /**
     * 局部变量 index 11 为 canSweep（1.21.1 Player.attack 字节码）。
     * 推进破击 + 剑时始终视为可横扫（与强制暴击可并存：首 store 即被抬为 true）。
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
