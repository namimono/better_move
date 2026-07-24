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
 * 推进破击：窗口内伤害按满蓄力结算（修推进瞬间弱击）；真实满蓄力才强制暴击；强制/扩大横扫；放宽触及。
 */
@Mixin(Player.class)
public abstract class PlayerAttackMixin {
    /**
     * 真实蓄力比例（未抬满前）。破击窗口会把 {@code getAttackStrengthScale} 抬成 1.0 以保证满伤，
     * 但暴击仍看这个真实值，避免「窗口内永远暴击」。
     */
    @Unique
    private static final ThreadLocal<Float> BOOSTERMOD$REAL_ATTACK_STRENGTH =
            ThreadLocal.withInitial(() -> Float.NaN);

    @Unique
    private boolean boostermod$spoofedForCrit;
    @Unique
    private boolean boostermod$savedSprinting;
    @Unique
    private boolean boostermod$savedOnGround;
    @Unique
    private float boostermod$savedFallDistance;

    @Inject(method = "attack", at = @At("HEAD"))
    private void boostermod$clearAttackStrengthCache(Entity target, CallbackInfo ci) {
        BOOSTERMOD$REAL_ATTACK_STRENGTH.remove();
        boostermod$spoofedForCrit = false;
    }

    /**
     * 破击窗口内：伤害结算按满蓄力（推进瞬间 / 连点也不会因半蓄力变成弱击）。
     * 原版只在 attack 开头读一次 scale，并同时用于「是否满蓄」布尔；此处缓存真实值后再决定是否抬满。
     */
    @Redirect(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getAttackStrengthScale(F)F"))
    private float boostermod$fullStrengthDamageInStrikeWindow(Player player, float partialTicks) {
        float real = player.getAttackStrengthScale(partialTicks);
        BOOSTERMOD$REAL_ATTACK_STRENGTH.set(real);
        if (BoostStrikeSupport.isBoostStrikeWindow(player)) {
            return 1.0f;
        }
        return real;
    }

    /**
     * 真实满蓄力时：伪装原版暴击前置（离地/下落/非疾跑）。
     * 推进推力期每 tick 会 resetFallDistance，需此路径才能稳定 ×1.5 与暴击特效。
     * 挂在 reset 前，使随后的暴击条件读到伪装状态。
     */
    @Inject(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;resetAttackStrengthTicker()V"))
    private void boostermod$spoofVanillaCritIfReallyFullCharge(Entity target, CallbackInfo ci) {
        if (!(target instanceof LivingEntity)) {
            return;
        }
        Player self = (Player) (Object) this;
        float real = BOOSTERMOD$REAL_ATTACK_STRENGTH.get();
        if (Float.isNaN(real) || !BoostStrikeSupport.shouldForceCritical(self, real)) {
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
        BOOSTERMOD$REAL_ATTACK_STRENGTH.remove();
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
     * 局部变量 index 9 为 critical（1.21.1）。
     * 因窗口内 scale 被抬满，原版 {@code bl=h>0.9} 会恒真；此处用<strong>真实</strong>蓄力决定是否强制暴击，
     * 半蓄力即使满伤也不抬暴击（并在未伪装时压回 false，避免推进中碰巧满足下落条件而暴击）。
     */
    @ModifyVariable(method = "attack", at = @At("STORE"), index = 9, ordinal = 0)
    private boolean boostermod$forceCriticalFlag(boolean critical, Entity target) {
        if (!(target instanceof LivingEntity)) {
            return critical;
        }
        Player self = (Player) (Object) this;
        float real = BOOSTERMOD$REAL_ATTACK_STRENGTH.get();
        if (Float.isNaN(real)) {
            return critical;
        }
        if (BoostStrikeSupport.shouldForceCritical(self, real)) {
            return true;
        }
        // 破击窗口内半蓄力：不要因 scale 被抬满 / 推进姿态而吃到暴击
        if (BoostStrikeSupport.isBoostStrikeWindow(self)) {
            return false;
        }
        return critical;
    }

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
            return BoostStrikeSupport.VANILLA_SWEEP_RANGE_SQR - 0.01;
        }
        return distanceSqr;
    }

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
