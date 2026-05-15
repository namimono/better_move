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
 *       落地，水平方向恒定走空气阻力，飞行距离不受触发时机影响。期间玩家的移动键（WASD）
 *       会按当前 tick 主推力的固定百分比叠加到水平速度上，作为对推进方向的轻微修正。</li>
 *   <li>推力结束 / 撞墙 / 玩家离线即收尾，停止 Y 锁后由 MC 重力自然接管落地。</li>
 * </ol>
 */
public final class BoosterMotionTicker {
    private static final int HYPER_Y_LOCK_DELAY_TICKS = 2;
    private static final ResourceLocation STEP_HEIGHT_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(BoosterMod.MOD_ID, "boost_step_height");
    /** 推进期间在默认 0.6 的基础上额外抬高 step height，使玩家能自动越过 1 格台阶。 */
    private static final double STEP_HEIGHT_BOOST = 0.5;
    /**
     * 地面起飞时的初始垂直冲量；等同于原版玩家的跳跃初速（0.42），保证当 tick 起就脱离
     * "ground friction = 0.546" 的物理分支，进入"air friction = 0.91"，使飞行距离与空中推进对齐。
     */
    private static final double GROUND_JUMP_KICK = 0.42;
    private static final double HYPER_IMPULSE_MULTIPLIER = 1.25;
    /**
     * 推进期间 A/D 键对推进方向的侧向修正强度，单位是"当前 tick 主推力的倍率"。
     * 取值 < 1 以保证只是"微调"，不会颠覆原有航向。
     */
    private static final double STEER_STRAFE_FACTOR = 0.35;
    /**
     * 推进期间 W/S 键对推进方向的纵向加/减速强度，单位是"当前 tick 主推力的倍率"。
     * 比侧向更弱，因为主推力本身就是向前的，再叠加同向加速容易破坏速度曲线。
     */
    private static final double STEER_FORWARD_FACTOR = 0.20;

    private static final Map<UUID, ActiveBoost> ACTIVE = new ConcurrentHashMap<>();
    /**
     * 玩家上报的"移动键修正输入"缓存。key = 玩家 UUID，value = {strafe, forward}。
     * 由 {@link com.boostermod.network.BoosterSteerPayload} 的接收器写入；只在推进 tick 时被读。
     * 不在推进期间也允许写入是为了避免接收器和推进生命周期之间出现复杂的状态机。
     */
    private static final Map<UUID, float[]> STEER_INPUT = new ConcurrentHashMap<>();

    private BoosterMotionTicker() {}

    public static boolean isBoosting(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    /** 由网络层调用，更新某个玩家最近一次上报的移动键输入。 */
    public static void setSteerInput(UUID playerId, float strafe, float forward) {
        if (strafe == 0.0f && forward == 0.0f) {
            STEER_INPUT.remove(playerId);
        } else {
            STEER_INPUT.put(playerId, new float[] {strafe, forward});
        }
    }

    public static void start(
            ServerLevel level,
            ServerPlayer player,
            Vec3 direction,
            BoosterBalanceProfile profile,
            Vec3 originEye,
            double eyeOffsetY,
            boolean hyper) {
        applyStepHeightBoost(player);

        double startY;
        if (player.onGround()) {
            startY = GROUND_JUMP_KICK;
            player.setOnGround(false);
        } else {
            // 空中触发时保留上扬动量、抹掉下落，避免推进过程被重力越拉越深。
            startY = Math.max(0.0, player.getDeltaMovement().y);
        }
        double impulse = profile.impulse() * (hyper ? HYPER_IMPULSE_MULTIPLIER : 1.0);
        player.setDeltaMovement(direction.x * impulse, startY, direction.z * impulse);
        player.resetFallDistance();
        player.hurtMarked = true;

        ACTIVE.put(
                player.getUUID(),
                new ActiveBoost(level, direction, profile, originEye, eyeOffsetY, hyper));
    }

    public static void tickServer(MinecraftServer server) {
        Iterator<Map.Entry<UUID, ActiveBoost>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ActiveBoost> entry = it.next();
            UUID id = entry.getKey();
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                it.remove();
                STEER_INPUT.remove(id);
                continue;
            }
            if (entry.getValue().step(player)) {
                stopBoost(player);
                it.remove();
                STEER_INPUT.remove(id);
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
        private final int yLockDelayTicks;
        private int tick;

        private ActiveBoost(
                ServerLevel level,
                Vec3 direction,
                BoosterBalanceProfile profile,
                Vec3 originEye,
                double eyeOffsetY,
                boolean hyper) {
            this.level = level;
            this.direction = direction;
            this.profile = profile;
            this.originEye = originEye;
            this.eyeOffsetY = eyeOffsetY;
            this.yLockDelayTicks = hyper ? HYPER_Y_LOCK_DELAY_TICKS : 0;
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

            // 读取由客户端通过 BoosterSteerPayload 同步过来的移动键输入：
            //   strafe 为左右（A=+1，D=-1），forward 为前后（W=+1，S=-1）。
            // 1.21.1 上原版 ServerboundPlayerInputPacket 只在骑乘载具时发送，所以服务端的
            // player.xxa/zza 在步行飞行场景下并不会反映 WASD 状态，只能走自有 payload。
            // 通过限幅 + 按 thrust 比例缩放，使其只能"微调"推进方向，无法掉头或失控加速。
            // 侧向基向量取 direction 在 MC 世界中"朝向左手"的方向（XZ 平面）：
            //   假设 direction = (0,0,-1) 即朝北，玩家左手指向西 (-1,0,0)；
            //   带入 (direction.z, -direction.x) = (-1, 0, 0) 正是西 ✓。
            // 配合约定 strafe = leftImpulse（A=+1, D=-1），最终 A→向左推、D→向右推。
            float[] steer = STEER_INPUT.get(player.getUUID());
            double strafeInput = steer == null ? 0.0 : Math.max(-1.0, Math.min(1.0, steer[0]));
            double forwardInput = steer == null ? 0.0 : Math.max(-1.0, Math.min(1.0, steer[1]));
            double sideThrust = thrust * STEER_STRAFE_FACTOR * strafeInput;
            double forwardSteer = thrust * STEER_FORWARD_FACTOR * forwardInput;
            double sideX = direction.z;
            double sideZ = -direction.x;

            // Y 锁 0：起跳冲量在 tick 1 已把玩家抬起，从此悬停在该高度，避免推进期间落地导致
            // 后续 tick 切换到 0.546 的地面摩擦分支。同时主动 setOnGround(false) 作为兜底，
            // 防止 MC 在某些边界情形里把玩家重新判定为"贴地"。
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(
                    v.x + direction.x * (thrust + forwardSteer) + sideX * sideThrust,
                    tick < yLockDelayTicks ? Math.max(0.0, v.y) : 0.0,
                    v.z + direction.z * (thrust + forwardSteer) + sideZ * sideThrust);
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
