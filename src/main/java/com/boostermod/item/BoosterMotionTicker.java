package com.boostermod.item;

import com.boostermod.BoosterMod;
import com.boostermod.balance.BoosterBalanceProfile;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * 推进器运动管理：给玩家一个初始水平冲量，随后在 {@code thrustTicks} 个 tick 内
 * 按线性衰减的方式追加推力，期间锁定 Y 速度为 0；推力阶段结束、撞墙或玩家离线即收尾。
 * 实际飞行轨迹由 Minecraft 自身的物理引擎（空气阻力、碰撞）决定，本类不主动计算距离与曲线。
 */
public final class BoosterMotionTicker {
    private static final ResourceLocation STEP_HEIGHT_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(BoosterMod.MOD_ID, "boost_step_height");
    /** 推进期间在默认 0.6 的基础上额外抬高 step height，使玩家能自动越过 1 格台阶。 */
    private static final double STEP_HEIGHT_BOOST = 0.5;

    private static final Map<UUID, ActiveBoost> ACTIVE = new ConcurrentHashMap<>();

    private BoosterMotionTicker() {}

    public static boolean isBoosting(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static void start(
            ServerLevel level,
            ServerPlayer player,
            Vec3 direction,
            BoosterBalanceProfile profile,
            Vec3 originEye,
            double eyeOffsetY) {
        applyStepHeightBoost(player);

        player.setDeltaMovement(direction.x * profile.impulse(), 0.0, direction.z * profile.impulse());
        player.resetFallDistance();
        player.hurtMarked = true;

        ACTIVE.put(
                player.getUUID(),
                new ActiveBoost(level, direction, profile, originEye, eyeOffsetY));
    }

    public static void tickServer(MinecraftServer server) {
        Iterator<Map.Entry<UUID, ActiveBoost>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ActiveBoost> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            if (entry.getValue().step(player)) {
                stopBoost(player);
                it.remove();
            }
        }
    }

    private static void stopBoost(ServerPlayer player) {
        removeStepHeightBoost(player);
    }

    private static void applyStepHeightBoost(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.STEP_HEIGHT);
        if (attr == null || attr.getModifier(STEP_HEIGHT_MODIFIER_ID) != null) {
            return;
        }
        attr.addTransientModifier(new AttributeModifier(
                STEP_HEIGHT_MODIFIER_ID, STEP_HEIGHT_BOOST, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void removeStepHeightBoost(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.STEP_HEIGHT);
        if (attr != null) {
            attr.removeModifier(STEP_HEIGHT_MODIFIER_ID);
        }
    }

    private static final class ActiveBoost {
        private final ServerLevel level;
        private final Vec3 direction;
        private final BoosterBalanceProfile profile;
        private final Vec3 originEye;
        private final double eyeOffsetY;
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

        private boolean step(ServerPlayer player) {
            if (player.horizontalCollision) {
                emitEndParticles(player.position());
                return true;
            }

            int totalTicks = profile.thrustTicks();
            if (totalTicks <= 0 || tick >= totalTicks) {
                emitEndParticles(player.position());
                return true;
            }

            double progress = (double) tick / totalTicks;
            double thrust = profile.thrustPerTick() * (1.0 - progress);

            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(
                    v.x + direction.x * thrust,
                    0.0,
                    v.z + direction.z * thrust);
            player.resetFallDistance();
            player.hurtMarked = true;

            tick++;
            return false;
        }

        private void emitEndParticles(Vec3 currentFeet) {
            Vec3 targetEye = currentFeet.add(0.0, eyeOffsetY, 0.0);
            BoosterLeggingsItem.emitTrailParticles(level, originEye, targetEye);
        }
    }
}
