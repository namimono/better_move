package com.bettermove.item;

import com.bettermove.tier.BoosterTier;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 以「速度脉冲」驱动推进：服务端持续把玩家的 {@code deltaMovement} 锁定为当前装备等级的推进速度，
 * 由原版物理推动玩家移动。这样客户端的 {@code LocalPlayer} 拿到的是
 * {@code ClientboundSetEntityMotionPacket}（由 {@link net.minecraft.world.entity.LivingEntity#hurtMarked}
 * 字段触发同步），后续移动由<strong>客户端本地物理 + 渲染帧插值</strong>完成，
 * 不会出现逐 tick teleport 的楼梯感。
 */
public final class BoosterMotionTicker {
    /** 单 tick 水平位移低于该值视为撞墙；推进立即中止。 */
    private static final double STUCK_PROGRESS = 0.05;
    /** 推进刚启动时给少量缓冲 tick，避免与起跳残留惯性/同步抖动对冲时被误判撞墙。 */
    private static final int STUCK_GRACE_TICKS = 2;
    /** 必须连续多个 tick 都几乎没推进，才视为真的撞墙。 */
    private static final int STUCK_CONSECUTIVE_TICKS = 3;
    /** 单次推进最长持续 tick 数兜底余量。 */
    private static final int MAX_TICK_PADDING = 6;

    private static final Map<UUID, ActiveBoost> ACTIVE = new ConcurrentHashMap<>();

    private BoosterMotionTicker() {}

    public static boolean isBoosting(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    /**
     * 启动推进：立即施加水平速度脉冲并登记到 tick 表。
     *
     * @param targetDistance 安全可达的水平距离
     * @param direction      水平方向单位向量（y 必须为 0）
     */
    public static void start(
            ServerLevel level,
            ServerPlayer player,
            Vec3 startFeet,
            double targetDistance,
            Vec3 direction,
            BoosterTier tier,
            Vec3 originEye,
            double eyeOffsetY) {
        double speed = tier.getSpeed();
        int plannedTicks = estimatePlannedTicks(targetDistance, speed);
        applyVelocity(player, direction, speedForTick(tier, tickProgress(0, plannedTicks)));
        ACTIVE.put(
                player.getUUID(),
                new ActiveBoost(level, startFeet, direction, targetDistance, tier, plannedTicks, originEye, eyeOffsetY));
    }

    /** 每服务端 tick 推进进行中的推进插值；在模组主入口注册。 */
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

    /**
     * 把玩家 deltaMovement 锁定为推进速度。y 分量直接置 0 = 推进期间无重力，
     * 这样从高处起推也不会一边推进一边坠落，行为更可预测。
     */
    private static void applyVelocity(ServerPlayer player, Vec3 direction, double speed) {
        player.setDeltaMovement(direction.x * speed, 0.0, direction.z * speed);
        player.resetFallDistance();
        player.hurtMarked = true;
    }

    /** 推进结束：清零水平速度，y 分量保留交还给重力。 */
    private static void stopBoost(ServerPlayer player) {
        Vec3 current = player.getDeltaMovement();
        player.setDeltaMovement(0.0, current.y, 0.0);
        player.hurtMarked = true;
    }

    private static final class ActiveBoost {
        private final ServerLevel level;
        private final Vec3 startFeet;
        private final Vec3 direction;
        private final double targetDistance;
        private final BoosterTier tier;
        private final int plannedTicks;
        private final Vec3 originEye;
        private final double eyeOffsetY;
        private double lastProgress;
        private int stalledTicks;
        private int tick;

        private ActiveBoost(
                ServerLevel level,
                Vec3 startFeet,
                Vec3 direction,
                double targetDistance,
                BoosterTier tier,
                int plannedTicks,
                Vec3 originEye,
                double eyeOffsetY) {
            this.level = level;
            this.startFeet = startFeet;
            this.direction = direction;
            this.targetDistance = targetDistance;
            this.tier = tier;
            this.plannedTicks = plannedTicks;
            this.originEye = originEye;
            this.eyeOffsetY = eyeOffsetY;
        }

        private boolean step(ServerPlayer player) {
            tick++;
            Vec3 current = player.position();
            double progress = forwardProgress(startFeet, current, direction);

            if (tick > STUCK_GRACE_TICKS) {
                if (progress - lastProgress < STUCK_PROGRESS) {
                    stalledTicks++;
                    if (stalledTicks >= STUCK_CONSECUTIVE_TICKS) {
                        emitEndParticles(current);
                        return true;
                    }
                } else {
                    stalledTicks = 0;
                }
            }
            lastProgress = progress;

            if (progress >= targetDistance - 0.05) {
                emitEndParticles(current);
                return true;
            }

            if (tick >= plannedTicks + MAX_TICK_PADDING) {
                emitEndParticles(current);
                return true;
            }

            // 喷射推进：前段快速点火，中段持续推力，末段快速断推。
            applyVelocity(player, direction, speedForTick(tier, tickProgress(tick, plannedTicks)));
            return false;
        }

        private void emitEndParticles(Vec3 current) {
            Vec3 targetEye = current.add(0.0, eyeOffsetY, 0.0);
            BoosterLeggingsItem.emitTrailParticles(level, originEye, targetEye);
        }

        private static double forwardProgress(Vec3 startFeet, Vec3 currentFeet, Vec3 direction) {
            double dx = currentFeet.x - startFeet.x;
            double dz = currentFeet.z - startFeet.z;
            return dx * direction.x + dz * direction.z;
        }
    }

    private static int estimatePlannedTicks(double targetDistance, double speed) {
        return Math.max(4, (int) Math.ceil(targetDistance / Math.max(speed, 1.0e-6)));
    }

    private static double tickProgress(int tick, int plannedTicks) {
        if (plannedTicks <= 1) {
            return 1.0;
        }
        return Math.min(1.0, (double) tick / (plannedTicks - 1));
    }

    private static double speedForTick(BoosterTier tier, double progress) {
        return tier.getSpeed() * jetSpeedMultiplier(progress, tier);
    }

    private static double jetSpeedMultiplier(double progress, BoosterTier tier) {
        double peakMultiplier = tier.getBoostStrength();
        double endMultiplier = tier.getEndSpeedMultiplier();
        if (progress < 0.15) {
            return lerp(progress / 0.15, 0.90, peakMultiplier);
        }
        if (progress < 0.70) {
            return lerp((progress - 0.15) / 0.55, peakMultiplier, 1.00);
        }
        return lerp((progress - 0.70) / 0.30, 1.00, endMultiplier);
    }

    private static double lerp(double t, double start, double end) {
        return start + (end - start) * t;
    }
}
