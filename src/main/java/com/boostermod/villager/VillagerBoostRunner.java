package com.boostermod.villager;

import com.boostermod.balance.BoosterBalanceManager;
import com.boostermod.balance.BoosterBalanceProfile;
import com.boostermod.item.BoosterLeggingsItem;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 村民推进：仅服务端、固定发起方向、等级基础平衡参数；不走玩家按键/网络/冷却容器。
 */
public final class VillagerBoostRunner {
    public static final double MIN_BOOST_DISTANCE = 6.0;
    public static final double MAX_BOOST_DISTANCE = 20.0;
    public static final int COOLDOWN_TICKS = 60;
    private static final double GROUND_JUMP_KICK = 0.42;
    private static final float BOOST_SOUND_VOLUME = 1.0f;
    private static final float BOOST_SOUND_PITCH = 1.2f;

    private static final Map<UUID, ActiveBoost> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();

    private VillagerBoostRunner() {}

    public static boolean isBoosting(Villager villager) {
        return ACTIVE.containsKey(villager.getUUID());
    }

    public static void clear(Villager villager) {
        ActiveBoost removed = ACTIVE.remove(villager.getUUID());
        if (removed != null) {
            stopBoost(villager, removed);
        }
        // 冷却挂在村民 UUID 上，脱离交战不重置。
    }

    public static void tickServer(MinecraftServer server) {
        Iterator<Map.Entry<UUID, ActiveBoost>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ActiveBoost> entry = it.next();
            ActiveBoost boost = entry.getValue();
            Villager villager = findVillager(server, entry.getKey(), boost.level);
            if (villager == null || villager.isRemoved() || !villager.isAlive()) {
                it.remove();
                continue;
            }
            if (boost.step(villager)) {
                stopBoost(villager, boost);
                it.remove();
            }
        }
    }

    /**
     * 交战中尝试发起村民推进；成功则立即损耗推进器 1 点耐久并进入冷却。
     * 升级项与 Hyper 一律忽略，只使用等级基础平衡参数。
     */
    public static boolean tryStartBoost(Villager villager, LivingEntity target) {
        if (!(villager.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (isBoosting(villager) || !canLaunchFromEnvironment(villager)) {
            return false;
        }
        if (isOnCooldown(villager)) {
            return false;
        }
        if (!villager.hasLineOfSight(target)) {
            return false;
        }

        double distance = villager.distanceTo(target);
        if (distance < MIN_BOOST_DISTANCE || distance > MAX_BOOST_DISTANCE) {
            return false;
        }
        if (hasSolidObstructionToward(villager, target)) {
            return false;
        }

        ItemStack legs = villager.getItemBySlot(EquipmentSlot.LEGS);
        if (!(legs.getItem() instanceof BoosterLeggingsItem boosterItem)) {
            return false;
        }

        Vec3 toTarget = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0)
                .subtract(villager.position().add(0.0, villager.getBbHeight() * 0.5, 0.0));
        if (toTarget.lengthSqr() < 1.0e-6) {
            return false;
        }
        Vec3 direction = toTarget.normalize();

        BoosterBalanceProfile balance =
                BoosterBalanceManager.get(serverLevel.getServer()).getProfile(boosterItem.getTier());
        Vec3 originEye = villager.getEyePosition();
        double eyeOffsetY = villager.getEyeY() - villager.getY();

        legs.hurtAndBreak(1, villager, EquipmentSlot.LEGS);
        playBoostSound(serverLevel, originEye);
        COOLDOWN_UNTIL.put(villager.getUUID(), serverLevel.getGameTime() + COOLDOWN_TICKS);

        // 耗尽耐久后立即退出武装：不残留推进运行状态。
        if (!(villager.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof BoosterLeggingsItem)) {
            return false;
        }

        villager.getNavigation().stop();
        villager.setOnGround(false);
        double startX = direction.x * balance.impulse();
        double startY = Math.max(GROUND_JUMP_KICK, direction.y * balance.impulse());
        double startZ = direction.z * balance.impulse();
        villager.setDeltaMovement(startX, startY, startZ);
        villager.resetFallDistance();
        villager.hurtMarked = true;

        ACTIVE.put(
                villager.getUUID(),
                new ActiveBoost(serverLevel, direction, balance, originEye, eyeOffsetY));
        return true;
    }

    /** 地面、非液体、非载具、非拴绳。 */
    static boolean canLaunchFromEnvironment(Villager villager) {
        return villager.onGround()
                && !villager.isInWaterOrBubble()
                && !villager.isInLava()
                && !villager.isPassenger()
                && !villager.isLeashed();
    }

    private static boolean isOnCooldown(Villager villager) {
        Long until = COOLDOWN_UNTIL.get(villager.getUUID());
        return until != null && villager.level().getGameTime() < until;
    }

    private static boolean hasSolidObstructionToward(Villager villager, LivingEntity target) {
        Vec3 from = villager.getEyePosition();
        Vec3 to = target.getEyePosition();
        HitResult hit = villager.level().clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, villager));
        if (hit.getType() == HitResult.Type.MISS) {
            return false;
        }
        // 命中点靠近目标时视为无阻挡（贴脸视线擦边）
        return hit.getLocation().distanceToSqr(to) > 1.0;
    }

    private static Villager findVillager(MinecraftServer server, UUID id, ServerLevel preferred) {
        if (preferred.getEntity(id) instanceof Villager villager) {
            return villager;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof Villager villager) {
                return villager;
            }
        }
        return null;
    }

    private static void stopBoost(Villager villager, ActiveBoost boost) {
        // 村民推进结束时恢复重力相关状态即可；不发送玩家反馈包。
        if (boost.grantedApexNoGravity) {
            villager.setNoGravity(false);
        }
    }

    private static void playBoostSound(ServerLevel level, Vec3 originEye) {
        level.playSound(
                null,
                originEye.x,
                originEye.y,
                originEye.z,
                SoundEvents.BREEZE_SHOOT,
                SoundSource.NEUTRAL,
                BOOST_SOUND_VOLUME,
                BOOST_SOUND_PITCH);
    }

    private static final class ActiveBoost {
        private final ServerLevel level;
        private final Vec3 direction;
        private final BoosterBalanceProfile profile;
        private final Vec3 originEye;
        private final double eyeOffsetY;
        private boolean grantedApexNoGravity;
        private int tick;

        private ActiveBoost(
                ServerLevel level,
                Vec3 direction,
                BoosterBalanceProfile profile,
                Vec3 originEye,
                double eyeOffsetY) {
            this.level = level;
            this.direction = direction;
            this.profile = profile;
            this.originEye = originEye;
            this.eyeOffsetY = eyeOffsetY;
        }

        private boolean step(Villager villager) {
            if (shouldEndBoost(villager)) {
                emitEndParticles(villager.position());
                return true;
            }

            int totalTicks = profile.thrustTicks();
            if (totalTicks <= 0 || tick >= totalTicks) {
                emitEndParticles(villager.position());
                return true;
            }

            double progress = (double) tick / totalTicks;
            double thrust = profile.thrustPerTick() * (1.0 - progress);
            Vec3 velocity = maybeSuppressGravityAtApex(villager, villager.getDeltaMovement());
            // 固定发起方向，不按视线持续追踪；忽略所有升级项与 Hyper。
            villager.setDeltaMovement(
                    velocity.x + direction.x * thrust,
                    velocity.y + direction.y * thrust,
                    velocity.z + direction.z * thrust);

            if (villager.onGround()) {
                villager.setOnGround(false);
            }
            villager.resetFallDistance();
            villager.hurtMarked = true;
            tick++;
            return false;
        }

        private boolean shouldEndBoost(Villager villager) {
            if (villager.horizontalCollision) {
                return true;
            }
            if (villager.isInWaterOrBubble() || villager.isInLava()) {
                return true;
            }
            // 失去推进器或剑等任一武装条件时立即结束，不残留运行状态。
            return !ArmedVillagerEquipment.isArmed(villager);
        }

        private Vec3 maybeSuppressGravityAtApex(Villager villager, Vec3 velocity) {
            if (grantedApexNoGravity || tick == 0 || velocity.y > 0.0 || villager.isNoGravity()) {
                return velocity;
            }
            grantedApexNoGravity = true;
            villager.setNoGravity(true);
            return new Vec3(velocity.x, 0.0, velocity.z);
        }

        private void emitEndParticles(Vec3 currentFeet) {
            Vec3 targetEye = currentFeet.add(0.0, eyeOffsetY, 0.0);
            BoosterLeggingsItem.emitTrailParticles(level, originEye, targetEye);
        }
    }
}
