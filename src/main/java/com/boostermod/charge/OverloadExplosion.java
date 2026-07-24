package com.boostermod.charge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 过载爆炸：首次固体/实体撞击触发一次；液体不触发。
 * power 3 + MOB（跟 mobGriefing）；自伤 generic 4.0，与爆炸 AOE 分离。
 */
public final class OverloadExplosion {
    public static final float POWER = 3.0F;
    public static final float SELF_DAMAGE = 4.0F;
    private static final double ENTITY_HIT_INFLATE = 0.1;

    /** 本 tick 刚触发过载爆炸的玩家（破击同帧跳过）。 */
    private static java.util.UUID explodedThisTickPlayer;
    private static int explodedThisTick;

    private OverloadExplosion() {}

    public static void detonate(ServerLevel level, ServerPlayer player) {
        double midY = player.getY() + player.getBbHeight() * 0.5;
        level.explode(
                player,
                player.getX(),
                midY,
                player.getZ(),
                POWER,
                false,
                Level.ExplosionInteraction.MOB);
        markExplodedThisTick(player);
        if (!player.getAbilities().instabuild && !player.isSpectator()) {
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().generic(), SELF_DAMAGE);
        }
    }

    public static boolean hitsEntity(ServerLevel level, ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(ENTITY_HIT_INFLATE);
        return !level.getEntities(
                player,
                box,
                OverloadExplosion::isImpactEntity).isEmpty();
    }

    public static boolean isSolidOrEntityImpact(ServerLevel level, ServerPlayer player) {
        return isSolidOrEntityImpact(level, player, false);
    }

    /**
     * @param ignoreVerticalBelow 地面起飞首段忽略脚下竖直碰撞，避免刚离地误炸
     */
    public static boolean isSolidOrEntityImpact(
            ServerLevel level, ServerPlayer player, boolean ignoreVerticalBelow) {
        if (player.horizontalCollision) {
            return true;
        }
        if (player.verticalCollision) {
            if (!(ignoreVerticalBelow && player.verticalCollisionBelow)) {
                return true;
            }
        }
        return hitsEntity(level, player);
    }

    public static boolean explodedThisTick(ServerPlayer player) {
        return player.getUUID().equals(explodedThisTickPlayer)
                && player.server.getTickCount() == explodedThisTick;
    }

    private static void markExplodedThisTick(ServerPlayer player) {
        explodedThisTickPlayer = player.getUUID();
        explodedThisTick = player.server.getTickCount();
    }

    private static boolean isImpactEntity(Entity entity) {
        if (!entity.isAlive() || !entity.isPickable()) {
            return false;
        }
        if (entity instanceof Player other && other.isSpectator()) {
            return false;
        }
        return true;
    }
}
