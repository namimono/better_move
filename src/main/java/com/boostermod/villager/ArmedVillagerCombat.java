package com.boostermod.villager;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 武装村民交战控制：锁敌、追击、脱离；交战时临时接管导航并压制普通日程。
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
            return true;
        }

        Player target = findLockTarget(villager, serverLevel);
        if (target == null) {
            return false;
        }

        beginEngagement(villager, target);
        tickCombatActions(villager, target);
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

    private static void tickCombatActions(Villager villager, Player target) {
        ArmedVillagerMelee.tickAttack(villager, target);
        if (!VillagerBoostRunner.isBoosting(villager)) {
            VillagerBoostRunner.tryStartBoost(villager, target);
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

    private static void beginEngagement(Villager villager, Player target) {
        interruptSchedule(villager);
        villager.setTarget(target);
        ENGAGEMENTS.put(
                villager.getUUID(),
                new Engagement(target, 0, villager.distanceTo(target)));
    }

    private static boolean maintainEngagement(Villager villager, ServerLevel level, Engagement engagement) {
        Player target = engagement.target();
        if (!isAttackablePlayer(target) || target.level() != level) {
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

    private static Player findLockTarget(Villager villager, ServerLevel level) {
        AABB box = villager.getBoundingBox().inflate(LOCK_RANGE);
        return level.getEntitiesOfClass(Player.class, box, ArmedVillagerCombat::isAttackablePlayer).stream()
                .filter(player -> villager.distanceTo(player) <= LOCK_RANGE)
                .filter(villager::hasLineOfSight)
                .min(Comparator.comparingDouble(villager::distanceTo))
                .orElse(null);
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

    private record Engagement(Player target, int lostApproachTicks, double lastDistance) {}
}
