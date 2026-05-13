package com.bettermove.item;

import com.bettermove.balance.DashBalanceProfile;
import com.bettermove.tier.DashTier;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 以「速度脉冲」驱动冲刺：服务端持续把玩家的 {@code deltaMovement} 锁定为当前装备等级的冲刺速度，
 * 由原版物理推动玩家移动。这样客户端的 {@code LocalPlayer} 拿到的是
 * {@code ClientboundSetEntityMotionPacket}（由 {@link net.minecraft.world.entity.LivingEntity#hurtMarked}
 * 字段触发同步），后续移动由<strong>客户端本地物理 + 渲染帧插值</strong>完成，
 * 绝对丝滑，没有原先「逐 tick teleport」造成的楼梯感。
 *
 * <h3>为什么不直接 teleport？</h3>
 * <p>{@code ServerPlayer.teleportTo} 发送的是 {@code ClientboundPlayerPositionPacket}，
 * 客户端对<strong>自己玩家</strong>的位置同步包是直接 {@code setPos}、不做任何插值的
 * （vanilla 只对其他实体做 lerp）。无论把动画分成多少 tick，玩家都会看到
 * 一连串硬切。换成速度脉冲后，客户端自己物理走，每渲染帧（60+ FPS）都有平滑过渡。</p>
 *
 * <h3>停止条件</h3>
 * <ul>
 *   <li>累计水平位移 ≥ {@code targetDistance}（服务端扫描的安全可达距离）</li>
 *   <li>单 tick 实际位移 &lt; {@link #STUCK_PROGRESS}（视为撞墙）</li>
 *   <li>触发 {@link #MAX_TICKS} 上限（兜底，正常不会到）</li>
 *   <li>玩家断线 / 实体不存在</li>
 * </ul>
 */
public final class DashMotionTicker {
    /** 单 tick 水平位移低于该值视为撞墙；冲刺立即中止。 */
    private static final double STUCK_PROGRESS = 0.05;
    /** 冲刺刚启动时给少量缓冲 tick，避免与起跳残留惯性/同步抖动对冲时被误判撞墙。 */
    private static final int STUCK_GRACE_TICKS = 2;
    /** 必须连续多个 tick 都几乎没推进，才视为真的撞墙。 */
    private static final int STUCK_CONSECUTIVE_TICKS = 3;
    /** 单次冲刺最长持续 tick 数兜底余量。 */
    private static final int MAX_TICK_PADDING = 6;

    private static final Map<UUID, ActiveDash> ACTIVE = new ConcurrentHashMap<>();

    private DashMotionTicker() {}

    public static boolean isDashing(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    /**
     * 启动冲刺：立即施加水平速度脉冲并登记到 tick 表。
     *
     * @param targetDistance 安全可达的<strong>水平</strong>距离（由 {@code findDashTarget} 扫描得到）
     * @param direction      水平方向单位向量（y 必须为 0）
     */
    public static void start(
            ServerLevel level,
            ServerPlayer player,
            Vec3 startFeet,
            double targetDistance,
            Vec3 direction,
            DashBalanceProfile profile,
            Vec3 originEye,
            double eyeOffsetY) {
        double speed = profile.speed();
        int plannedTicks = estimatePlannedTicks(targetDistance, speed);
        applyVelocity(player, direction, speedForTick(profile, tickProgress(0, plannedTicks)));
        ACTIVE.put(
                player.getUUID(),
                new ActiveDash(level, startFeet, direction, targetDistance, profile, plannedTicks, originEye, eyeOffsetY));
    }

    /**
     * 每服务端 tick 推进进行中的冲刺插值；在模组主入口注册。
     * @param server 服务端实例
     */
    public static void tickServer(MinecraftServer server) {
        Iterator<Map.Entry<UUID, ActiveDash>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ActiveDash> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            if (e.getValue().step(player)) {
                stopDash(player);
                it.remove();
            }
        }
    }

    /**
     * 把玩家 deltaMovement 锁定为冲刺速度。y 分量直接置 0 = 冲刺期间无重力，
     * 这样从高处起冲也不会一边冲一边坠落，行为更可预测。
     * {@code hurtMarked = true} 是 vanilla 触发实体速度同步包的标准方式。
     */
    private static void applyVelocity(ServerPlayer player, Vec3 direction, double speed) {
        player.setDeltaMovement(direction.x * speed, 0.0, direction.z * speed);
        player.resetFallDistance();
        player.hurtMarked = true;
    }

    /** 冲刺结束：清零水平速度，y 分量保留交还给重力。 */
    private static void stopDash(ServerPlayer player) {
        Vec3 cur = player.getDeltaMovement();
        player.setDeltaMovement(0.0, cur.y, 0.0);
        player.hurtMarked = true;
    }

    private static final class ActiveDash {
        private final ServerLevel level;
        private final Vec3 startFeet;
        private final Vec3 direction;
        private final double targetDistance;
        private final DashBalanceProfile profile;
        private final int plannedTicks;
        private final Vec3 originEye;
        private final double eyeOffsetY;
        private double lastProgress;
        private int stalledTicks;
        private int tick;

        private ActiveDash(
                ServerLevel level,
                Vec3 startFeet,
                Vec3 direction,
                double targetDistance,
                DashBalanceProfile profile,
                int plannedTicks,
                Vec3 originEye,
                double eyeOffsetY) {
            this.level = level;
            this.startFeet = startFeet;
            this.direction = direction;
            this.targetDistance = targetDistance;
            this.profile = profile;
            this.plannedTicks = plannedTicks;
            this.originEye = originEye;
            this.eyeOffsetY = eyeOffsetY;
        }

        /**
         * @return {@code true} 表示动画已结束，应从表中移除。
         */
        private boolean step(ServerPlayer player) {
            tick++;
            Vec3 cur = player.position();
            double progress = forwardProgress(startFeet, cur, direction);

            // 撞墙检测看"沿冲刺方向的净进度"，并给起步两 tick 缓冲，
            // 避免反向空中冲刺时被上一段惯性/同步抖动误判成卡墙。
            if (tick > STUCK_GRACE_TICKS) {
                if (progress - lastProgress < STUCK_PROGRESS) {
                    stalledTicks++;
                    if (stalledTicks >= STUCK_CONSECUTIVE_TICKS) {
                        emitEndParticles(cur);
                        return true;
                    }
                } else {
                    stalledTicks = 0;
                }
            }
            lastProgress = progress;

            // 走完目标距离，落幕
            if (progress >= targetDistance - 0.05) {
                emitEndParticles(cur);
                return true;
            }

            if (tick >= plannedTicks + MAX_TICK_PADDING) {
                emitEndParticles(cur);
                return true;
            }

            // 喷射推进：前段快速点火，中段持续推力，末段快速断推。
            applyVelocity(player, direction, speedForTick(profile, tickProgress(tick, plannedTicks)));
            return false;
        }

        private void emitEndParticles(Vec3 cur) {
            Vec3 targetEye = cur.add(0.0, eyeOffsetY, 0.0);
            DashToolItem.emitTrailParticles(level, originEye, targetEye);
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

    private static double speedForTick(DashBalanceProfile profile, double progress) {
        return profile.speed() * jetSpeedMultiplier(progress, profile);
    }

    private static double jetSpeedMultiplier(double progress, DashBalanceProfile profile) {
        double peakMultiplier = profile.boostStrength();
        double endMultiplier = profile.endSpeedMultiplier();
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
