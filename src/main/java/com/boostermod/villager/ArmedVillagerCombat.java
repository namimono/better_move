package com.boostermod.villager;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 武装村民交战控制：锁敌、追击、脱离；交战时临时接管导航并压制普通日程。
 * 锁敌目标类型由 {@link ArmedVillagerSettings} 配置（玩家 / 怪物）。
 */
public final class ArmedVillagerCombat {
    public static final double LOCK_RANGE = 32.0;
    public static final double DISENGAGE_RANGE = 48.0;
    public static final int LOST_APPROACH_TICKS = 200;
    private static final double CHASE_SPEED = 0.6;

    private static final Map<UUID, Engagement> ENGAGEMENTS = new ConcurrentHashMap<>();

    private ArmedVillagerCombat() {}

    public static boolean isEngaged(Villager villager) {
        return ENGAGEMENTS.containsKey(villager.getUUID());
    }

    /**
     * 推进交战状态机。
     *
     * @return true 表示本 tick 已接管 AI，调用方应跳过原版 Brain。
     */
    public static boolean tickEngagement(Villager villager) {
        Level level = villager.level();
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        if (!ArmedVillagerEquipment.isArmed(villager) || serverLevel.getDifficulty() == Difficulty.PEACEFUL) {
            clearEngagement(villager);
            return false;
        }

        Engagement engagement = ENGAGEMENTS.get(villager.getUUID());
        if (engagement != null) {
            if (!maintainEngagement(villager, serverLevel, engagement)) {
                clearEngagement(villager);
                return false;
            }
            tickCombatActions(villager, engagement.target());
            if (!ArmedVillagerEquipment.isArmed(villager)) {
                clearEngagement(villager);
                return false;
            }
            return true;
        }

        LivingEntity target = findLockTarget(villager, serverLevel);
        if (target == null) {
            return false;
        }

        beginEngagement(villager, target);
        tickCombatActions(villager, target);
        if (!ArmedVillagerEquipment.isArmed(villager)) {
            clearEngagement(villager);
            return false;
        }
        return true;
    }

    public static void clearEngagement(Villager villager) {
        ArmedVillagerMelee.clear(villager);
        VillagerBoostRunner.clear(villager);
        if (ENGAGEMENTS.remove(villager.getUUID()) != null) {
            villager.setTarget(null);
            villager.getNavigation().stop();
        }
    }

    /** 切换锁敌模式等场景：清空全部进行中的交战。 */
    public static void clearAllEngagements(MinecraftServer server) {
        for (UUID id : List.copyOf(ENGAGEMENTS.keySet())) {
            Villager villager = null;
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(id) instanceof Villager found) {
                    villager = found;
                    break;
                }
            }
            if (villager != null) {
                clearEngagement(villager);
            } else {
                ENGAGEMENTS.remove(id);
            }
        }
        ArmedVillagerMelee.clearAll();
        VillagerBoostRunner.clearAll(server);
    }

    private static void tickCombatActions(Villager villager, LivingEntity target) {
        ArmedVillagerMelee.tickAttack(villager, target);
        if (!VillagerBoostRunner.isBoosting(villager)) {
            VillagerBoostRunner.tryStartBoost(villager, target);
        }
        // 推进耗尽耐久等导致退出武装后，本 tick 不再追击或继续攻击编排。
        if (!ArmedVillagerEquipment.isArmed(villager)) {
            return;
        }
        if (!VillagerBoostRunner.isBoosting(villager)) {
            chase(villager, target);
        }
    }

    public static boolean isAttackablePlayer(Player player) {
        if (!player.isAlive() || player.isRemoved() || player.getHealth() <= 0.0F) {
            return false;
        }
        if (player.isSpectator() || player.isCreative()) {
            return false;
        }
        return player.canBeSeenAsEnemy();
    }

    public static boolean isAttackableMonster(LivingEntity entity) {
        if (!(entity instanceof Monster)) {
            return false;
        }
        if (!entity.isAlive() || entity.isRemoved() || entity.getHealth() <= 0.0F) {
            return false;
        }
        return true;
    }

    /** 按当前服务端配置判断实体是否可作为锁敌目标。 */
    public static boolean isAttackableTarget(LivingEntity entity, ArmedVillagerTargetMode mode) {
        return switch (mode) {
            case PLAYERS -> entity instanceof Player player && isAttackablePlayer(player);
            case MONSTERS -> isAttackableMonster(entity);
        };
    }

    private static void beginEngagement(Villager villager, LivingEntity target) {
        interruptSchedule(villager);
        villager.setTarget(target);
        ENGAGEMENTS.put(
                villager.getUUID(),
                new Engagement(target, 0, villager.distanceTo(target)));
    }

    private static boolean maintainEngagement(Villager villager, ServerLevel level, Engagement engagement) {
        LivingEntity target = engagement.target();
        ArmedVillagerTargetMode mode = ArmedVillagerSettings.get(level.getServer()).getTargetMode();
        if (!isAttackableTarget(target, mode) || target.level() != level) {
            return false;
        }

        double distance = villager.distanceTo(target);
        if (distance > DISENGAGE_RANGE) {
            return false;
        }

        boolean visible = villager.hasLineOfSight(target);
        boolean approaching = distance < engagement.lastDistance() - 0.05;
        int lostTicks = engagement.lostApproachTicks();
        if (visible || approaching) {
            lostTicks = 0;
        } else {
            lostTicks++;
        }
        if (lostTicks >= LOST_APPROACH_TICKS) {
            return false;
        }

        ENGAGEMENTS.put(
                villager.getUUID(),
                new Engagement(target, lostTicks, distance));
        villager.setTarget(target);
        return true;
    }

    private static LivingEntity findLockTarget(Villager villager, ServerLevel level) {
        ArmedVillagerTargetMode mode = ArmedVillagerSettings.get(level.getServer()).getTargetMode();
        AABB box = villager.getBoundingBox().inflate(LOCK_RANGE);
        return switch (mode) {
            case PLAYERS -> level.getEntitiesOfClass(Player.class, box, ArmedVillagerCombat::isAttackablePlayer).stream()
                    .filter(player -> villager.distanceTo(player) <= LOCK_RANGE)
                    .filter(villager::hasLineOfSight)
                    .min(Comparator.comparingDouble(villager::distanceTo))
                    .map(LivingEntity.class::cast)
                    .orElse(null);
            case MONSTERS -> level.getEntitiesOfClass(Monster.class, box, ArmedVillagerCombat::isAttackableMonster)
                    .stream()
                    .filter(monster -> villager.distanceTo(monster) <= LOCK_RANGE)
                    .filter(villager::hasLineOfSight)
                    .min(Comparator.comparingDouble(villager::distanceTo))
                    .map(LivingEntity.class::cast)
                    .orElse(null);
        };
    }

    private static void chase(Villager villager, LivingEntity target) {
        interruptSchedule(villager);
        villager.setTarget(target);
        PathNavigation navigation = villager.getNavigation();
        if (!navigation.isDone()
                && navigation.getTargetPos() != null
                && navigation.getTargetPos().closerToCenterThan(target.position(), 1.5)) {
            return;
        }
        navigation.moveTo(target, CHASE_SPEED);
    }

    private static void interruptSchedule(Villager villager) {
        if (villager.isSleeping()) {
            villager.stopSleeping();
        }
        if (villager.isTrading()) {
            villager.setTradingPlayer(null);
        }
    }

    private record Engagement(LivingEntity target, int lostApproachTicks, double lastDistance) {}
}
