package com.bettermove.item;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 以「速度脉冲」驱动冲刺：服务端持续把玩家的 {@code deltaMovement} 锁定为冲刺速度，
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
    /**
     * 冲刺水平速度（格/tick）。1.0 ≈ 20 格/秒，约疾跑（0.28 格/tick）的 3.5 倍。
     *
     * <p>注意：单次 tick 水平位移最好别超过 ~10 格，否则会触发 vanilla
     * {@code ServerGamePacketListenerImpl} 的 movedWrongly 检测警告（不会踢人，
     * 但日志会刷屏）。</p>
     */
    public static final double DASH_SPEED = 1.0;

    /** 单 tick 水平位移低于该值视为撞墙；冲刺立即中止。 */
    private static final double STUCK_PROGRESS = 0.05;

    /** 单次冲刺最长持续 tick 数，兜底上限。理论上 {@code 18 / 1.0 + 余量} 已足够。 */
    private static final int MAX_TICKS = 40;

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
            Vec3 originEye,
            double eyeOffsetY) {
        applyVelocity(player, direction);
        ACTIVE.put(
                player.getUUID(),
                new ActiveDash(level, startFeet, direction, targetDistance, originEye, eyeOffsetY));
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
    private static void applyVelocity(ServerPlayer player, Vec3 direction) {
        player.setDeltaMovement(direction.x * DASH_SPEED, 0.0, direction.z * DASH_SPEED);
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
        private final Vec3 originEye;
        private final double eyeOffsetY;
        private Vec3 lastPos;
        private int tick;

        private ActiveDash(
                ServerLevel level,
                Vec3 startFeet,
                Vec3 direction,
                double targetDistance,
                Vec3 originEye,
                double eyeOffsetY) {
            this.level = level;
            this.startFeet = startFeet;
            this.direction = direction;
            this.targetDistance = targetDistance;
            this.originEye = originEye;
            this.eyeOffsetY = eyeOffsetY;
        }

        /**
         * @return {@code true} 表示动画已结束，应从表中移除。
         */
        private boolean step(ServerPlayer player) {
            tick++;
            Vec3 cur = player.position();

            // 撞墙检测：上一 tick 设了大速度，本 tick 玩家几乎没动 —— vanilla 物理被墙挡住了
            if (lastPos != null && horizontalDistance(lastPos, cur) < STUCK_PROGRESS) {
                emitEndParticles(cur);
                return true;
            }
            lastPos = cur;

            // 走完目标距离，落幕
            if (horizontalDistance(startFeet, cur) >= targetDistance - 0.05) {
                emitEndParticles(cur);
                return true;
            }

            if (tick >= MAX_TICKS) {
                emitEndParticles(cur);
                return true;
            }

            // 继续维持速度：vanilla 每 tick 会因摩擦/碰撞衰减 deltaMovement，必须重置
            applyVelocity(player, direction);
            return false;
        }

        private void emitEndParticles(Vec3 cur) {
            Vec3 targetEye = cur.add(0.0, eyeOffsetY, 0.0);
            DashToolItem.emitTrailParticles(level, originEye, targetEye);
        }

        private static double horizontalDistance(Vec3 a, Vec3 b) {
            double dx = a.x - b.x;
            double dz = a.z - b.z;
            return Math.sqrt(dx * dx + dz * dz);
        }
    }
}
