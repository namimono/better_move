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
 * 推进器运动管理：
 * <ol>
 *   <li>{@link #start} 时给一个水平初速度；如果玩家在地面则同时给一个跳跃式垂直冲量并强制
 *       离地，使本 tick 就走 0.91 的"空中阻力"分支而非 0.546 的"地面摩擦"分支。</li>
 *   <li>{@link ActiveBoost#step} 每 tick 沿水平方向追加线性衰减的推力，并将 Y 速度锁回 0，
 *       让玩家在起跳达到的高度上悬停推进；这样无论是地面触发还是空中触发，推进期间都不会
 *       落地，水平方向恒定走空气阻力，飞行距离不受触发时机影响。</li>
 *   <li>推力结束 / 撞墙 / 玩家离线即收尾，停止 Y 锁后由 MC 重力自然接管落地。</li>
 * </ol>
 */
public final class BoosterMotionTicker {
    private static final ResourceLocation STEP_HEIGHT_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(BoosterMod.MOD_ID, "boost_step_height");
    /** 推进期间在默认 0.6 的基础上额外抬高 step height，使玩家能自动越过 1 格台阶。 */
    private static final double STEP_HEIGHT_BOOST = 0.5;
    /**
     * 地面起飞时的初始垂直冲量；等同于原版玩家的跳跃初速（0.42），保证当 tick 起就脱离
     * "ground friction = 0.546" 的物理分支，进入"air friction = 0.91"，使飞行距离与空中推进对齐。
     */
    private static final double GROUND_JUMP_KICK = 0.42;

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

        double startY;
        if (player.onGround()) {
            startY = GROUND_JUMP_KICK;
            player.setOnGround(false);
        } else {
            // 空中触发时保留上扬动量、抹掉下落，避免推进过程被重力越拉越深。
            startY = Math.max(0.0, player.getDeltaMovement().y);
        }
        player.setDeltaMovement(direction.x * profile.impulse(), startY, direction.z * profile.impulse());
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

            // Y 锁 0：起跳冲量在 tick 1 已把玩家抬起，从此悬停在该高度，避免推进期间落地导致
            // 后续 tick 切换到 0.546 的地面摩擦分支。同时主动 setOnGround(false) 作为兜底，
            // 防止 MC 在某些边界情形里把玩家重新判定为"贴地"。
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(
                    v.x + direction.x * thrust,
                    0.0,
                    v.z + direction.z * thrust);
            if (player.onGround()) {
                player.setOnGround(false);
            }
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
