package com.boostermod.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 推进破击命中辅助：不依赖准星精确射线，按「贴身优先 + 视角锥」选取最近合法目标。
 * 解决高速推进时「感觉能打中却挥空」的问题。
 */
public final class BoostStrikeTargeting {
    private BoostStrikeTargeting() {}

    /**
     * 在推进破击窗口内查找本次攻击应优先锁定的生物；无合适目标返回 null。
     */
    public static LivingEntity findAssistTarget(Player player) {
        if (!BoostStrikeSupport.isBoostStrikeWindow(player)) {
            return null;
        }

        Level level = player.level();
        double range = BoostStrikeSupport.assistRange(player);
        double rangeSqr = range * range;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        if (look.lengthSqr() < 1.0e-6) {
            look = player.getLookAngle();
        }
        look = look.normalize();

        AABB search = player.getBoundingBox()
                .inflate(range, range * 0.75, range)
                .expandTowards(look.scale(range));

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (LivingEntity entity : level.getEntitiesOfClass(
                LivingEntity.class,
                search,
                candidate -> isValidTarget(player, candidate))) {
            AABB box = entity.getBoundingBox();
            boolean bodyTouch = player.getBoundingBox()
                    .inflate(BoostStrikeSupport.BODY_HIT_MARGIN)
                    .intersects(box);
            double eyeDistSqr = box.distanceToSqr(eye);
            if (!bodyTouch && eyeDistSqr > rangeSqr) {
                continue;
            }

            Vec3 center = box.getCenter();
            Vec3 toCenter = center.subtract(eye);
            double along = toCenter.dot(look);
            // 身后目标：仅贴身碰撞才收
            if (!bodyTouch && along < -0.35) {
                continue;
            }

            double rayT = Math.max(0.0, along);
            Vec3 onRay = eye.add(look.scale(rayT));
            double lateralSqr = onRay.distanceToSqr(center);
            double bodyDistSqr = player.distanceToSqr(entity);

            if (!bodyTouch) {
                // 偏离视线过远则放弃（约 2.5 格侧向）
                if (lateralSqr > BoostStrikeSupport.ASSIST_MAX_LATERAL_SQR) {
                    continue;
                }
            }

            double score;
            if (bodyTouch) {
                // 贴身几乎必中
                score = bodyDistSqr * 0.05;
            } else {
                // 侧向偏差权重大于距离，贴视线的优先
                score = lateralSqr * 3.5 + bodyDistSqr * 0.35 + Math.max(0.0, -along) * 2.0;
            }

            if (score < bestScore) {
                bestScore = score;
                best = entity;
            }
        }

        return best;
    }

    public static boolean isWithinForgivingReach(Player player, LivingEntity target) {
        if (!BoostStrikeSupport.isBoostStrikeWindow(player) || target == null) {
            return false;
        }
        if (!isValidTarget(player, target)) {
            return false;
        }
        AABB box = target.getBoundingBox();
        if (player.getBoundingBox().inflate(BoostStrikeSupport.BODY_HIT_MARGIN).intersects(box)) {
            return true;
        }
        double range = BoostStrikeSupport.assistRange(player) + BoostStrikeSupport.SERVER_REACH_SLACK;
        return box.distanceToSqr(player.getEyePosition()) < range * range;
    }

    private static boolean isValidTarget(Player player, LivingEntity candidate) {
        if (candidate == null || candidate == player || !candidate.isAlive() || candidate.isRemoved()) {
            return false;
        }
        if (candidate.isSpectator()) {
            return false;
        }
        // 尊重原版友方 / 不可攻击规则
        return player.canAttack(candidate);
    }
}
