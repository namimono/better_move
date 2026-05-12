package com.bettermove.item;

import com.bettermove.network.DashRequestPayload;
import com.bettermove.tier.DashTier;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 冲刺突进装备。装备在玩家腿部槽位，按客户端绑定的冲刺键沿<strong>水平移动方向</strong>冲刺
 * （站立或空中几乎无位移时退化为水平朝向）；通过 {@link DashMotionTicker} 给玩家施加
 * 一个按装备等级决定速度的水平脉冲，剩余移动交给原版物理推进，依靠客户端本地物理 + 渲染帧插值实现丝滑。
 *
 * <p>核心算法——「沿冲刺方向扫描玩家碰撞箱 + step-up 修正」：
 * 以 {@value #SCAN_STEP} 格步长沿冲刺方向平移玩家碰撞箱并检测碰撞；若发生碰撞
 * （最常见的是朝下视角时玩家箱压进自己脚下地面），沿 +y 小幅抬高最多
 * {@value #STEP_UP_MAX} 格再试，让玩家"贴着地面斜坡前进"。这样朝下视角也能突进，
 * 而不是直接判失败。</p>
 *
 * <ul>
 *   <li>距离、速度与耐久由 {@link DashTier} 决定</li>
 *   <li>所有等级冷却 3 秒（{@value #COOLDOWN_TICKS} ticks）</li>
 *   <li>每次消耗饥饿（{@value #HUNGER_EXHAUSTION} 疲劳值）+ 1 点耐久</li>
 *   <li>仅当玩家完全无法前进（已经被卡死）才取消冷却</li>
 *   <li>右键空气会把它穿到腿部槽（同原版护甲行为，由 {@link Equipable} 接口提供）</li>
 *   <li>冲刺触发逻辑见 {@link #tryDashFromKey(ServerPlayer)}</li>
 * </ul>
 */
public class DashToolItem extends Item implements Equipable {
    private static final int COOLDOWN_TICKS = 60;
    /** {@link Player#causeFoodExhaustion(float)} 用量；略低于旧版 2.0，减轻「一冲就饿」。 */
    private static final float MOVEMENT_DASH_HUNGER_EXHAUSTION = 1.0f;
    /** 保留 main 分支旧实现：视线冲刺使用瞬移 + 2.0 饥饿消耗。 */
    private static final float LOOK_DASH_HUNGER_EXHAUSTION = 2.0f;

    /** 沿冲刺方向扫描时的步长（格）。 */
    private static final double SCAN_STEP = 0.25;
    /** 命中阻挡后做二分细化的次数；总精度 ≈ SCAN_STEP / 2^REFINE_ITERS。 */
    private static final int REFINE_ITERS = 5;
    /** 沿冲刺方向推进距离低于该值就视为「贴脸了，没法前进」。 */
    private static final double MIN_DASH_DISTANCE = 0.1;

    /** step-up 修正的最大抬高量（格），约一个完整台阶高度。 */
    private static final double STEP_UP_MAX = 1.0;
    /** step-up 修正的试探步长。 */
    private static final double STEP_UP_GRAIN = 0.05;

    /** 饥饿值低于该值禁止突进，避免饿到掉血时还能瞎冲。 */
    private static final int MIN_FOOD_LEVEL = 6;
    /**
     * 目前仍默认使用“移动方向冲刺”。
     * 旧的“视线方向冲刺”逻辑先完整保留在代码里，后续可挂到装备升级项上。
     */
    private static final DashMode ACTIVE_DASH_MODE = DashMode.MOVEMENT_DIRECTION;

    private final DashTier tier;

    private enum DashMode {
        MOVEMENT_DIRECTION,
        LOOK_DIRECTION
    }

    public DashToolItem(Properties properties, DashTier tier) {
        super(properties);
        this.tier = tier;
    }

    public DashTier getTier() {
        return tier;
    }

    // ---------------------------------------------------------------- Equipable

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.LEGS;
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    /**
     * 右键空气把物品穿到腿部槽（与原版护甲一致）。
     * 若已经穿着另一件护甲会自动交换。冲刺动作不再由右键触发，统一由按键事件处理。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return this.swapWithEquipmentSlot(this, level, player, hand);
    }

    // ----------------------------------------------------------- Dash entrypoint

    /**
     * 服务端按键入口：玩家按下冲刺键，由网络层路由到此处。
     *
     * <p>关键校验（装备 / 冷却 / 饥饿 / 距离）都以服务端权威状态为准。冲刺方向
     * 由客户端在按键瞬间把自己物理模拟的水平速度 ({@code clientDirX/Z}) 一并送上来：
     * 服务端的 {@code ServerPlayer.getDeltaMovement()} 和 {@code xo/zo} 在 vanilla
     * 走的 {@code absMoveTo} 路径下水平分量恒为 0，唯一可靠的来源是客户端测量值。
     * 客户端就算伪造方向也只能影响自身一次冲刺去向，没有 exploit 空间。</p>
     */
    public static void tryDashFromKey(ServerPlayer player, double clientDirX, double clientDirZ) {
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        if (!(legs.getItem() instanceof DashToolItem dashItem)) {
            return;
        }
        if (DashMotionTicker.isDashing(player)) {
            return;
        }
        if (player.getCooldowns().isOnCooldown(dashItem)) {
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        if (!creative && player.getFoodData().getFoodLevel() < MIN_FOOD_LEVEL) {
            return;
        }

        ServerLevel level = player.serverLevel();
        Vec3 startFeet = player.position();
        Vec3 targetFeet = dashItem.findDashTarget(level, player, clientDirX, clientDirZ);
        if (targetFeet == null) {
            return;
        }

        dashItem.applyDash(level, player, legs, startFeet, targetFeet);
        player.getCooldowns().addCooldown(dashItem, COOLDOWN_TICKS);
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    /** 每服务端 tick 推进进行中的冲刺插值；在模组主入口注册。 */
    public static void tickActiveMotions(MinecraftServer server) {
        DashMotionTicker.tickServer(server);
    }

    /**
     * 沿冲刺方向扫描，找出玩家碰撞箱可平移到的最远 feet 位置。
     *
     * @return 可前进的目标坐标；若沿该方向推进 < {@value #MIN_DASH_DISTANCE} 格视为无效
     */
    private Vec3 findDashTarget(Level level, Player player, double clientDirX, double clientDirZ) {
        return switch (ACTIVE_DASH_MODE) {
            case MOVEMENT_DIRECTION -> findMovementDashTarget(level, player, clientDirX, clientDirZ);
            case LOOK_DIRECTION -> findLookDashTarget(level, player);
        };
    }

    /**
     * 当前启用的实现：优先按玩家水平移动方向扫描；站立时退化到水平视线方向。
     */
    private Vec3 findMovementDashTarget(Level level, Player player, double clientDirX, double clientDirZ) {
        Vec3 dir = horizontalDashDirection(player, clientDirX, clientDirZ);
        if (dir.lengthSqr() < 1.0e-6) {
            return null;
        }

        AABB origBox = player.getBoundingBox();
        Vec3 origin = player.position();
        double maxDist = tier.getDistance();

        // bestT: 沿冲刺方向的标称推进距离（决定有没有"前进"）
        // bestOffset: 经过 step-up 修正后的实际位移（决定玩家最终落点）
        double bestT = 0.0;
        Vec3 bestOffset = Vec3.ZERO;
        boolean blocked = false;

        for (double t = SCAN_STEP; t <= maxDist + 1.0e-6; t += SCAN_STEP) {
            Vec3 fitted = tryFitOffset(level, player, origBox, dir.scale(t));
            if (fitted != null) {
                bestT = t;
                bestOffset = fitted;
            } else {
                blocked = true;
                break;
            }
        }

        // 阻挡后二分细化沿冲刺方向的进度，让玩家停得更靠近墙
        if (blocked) {
            double lo = bestT;
            double hi = Math.min(bestT + SCAN_STEP, maxDist);
            for (int i = 0; i < REFINE_ITERS; i++) {
                double mid = (lo + hi) * 0.5;
                Vec3 fitted = tryFitOffset(level, player, origBox, dir.scale(mid));
                if (fitted != null) {
                    lo = mid;
                    bestOffset = fitted;
                } else {
                    hi = mid;
                }
            }
            bestT = lo;
        }

        // 用 bestT（冲刺方向标量进度）而不是 bestOffset.length 判定，避免「原地抬升」误判为成功
        if (bestT < MIN_DASH_DISTANCE) {
            return null;
        }
        return origin.add(bestOffset);
    }

    /**
     * 保留 main 分支旧实现：直接沿视线方向扫描，允许向上/向下看时带 y 分量。
     * 目前不启用，后续可挂到装备升级项或配置开关。
     */
    private Vec3 findLookDashTarget(Level level, Player player) {
        Vec3 lookVec = player.getViewVector(1.0f);
        if (lookVec.lengthSqr() < 1.0e-6) {
            return null;
        }
        Vec3 dir = lookVec.normalize();

        AABB origBox = player.getBoundingBox();
        Vec3 origin = player.position();
        double maxDist = tier.getDistance();

        double bestT = 0.0;
        Vec3 bestOffset = Vec3.ZERO;
        boolean blocked = false;

        for (double t = SCAN_STEP; t <= maxDist + 1.0e-6; t += SCAN_STEP) {
            Vec3 fitted = tryFitOffset(level, player, origBox, dir.scale(t));
            if (fitted != null) {
                bestT = t;
                bestOffset = fitted;
            } else {
                blocked = true;
                break;
            }
        }

        if (blocked) {
            double lo = bestT;
            double hi = Math.min(bestT + SCAN_STEP, maxDist);
            for (int i = 0; i < REFINE_ITERS; i++) {
                double mid = (lo + hi) * 0.5;
                Vec3 fitted = tryFitOffset(level, player, origBox, dir.scale(mid));
                if (fitted != null) {
                    lo = mid;
                    bestOffset = fitted;
                } else {
                    hi = mid;
                }
            }
            bestT = lo;
        }

        if (bestT < MIN_DASH_DISTANCE) {
            return null;
        }
        return origin.add(bestOffset);
    }

    /**
     * 尝试一个理想水平冲刺位移；若与方块碰撞，沿 +y 抬高最多 {@link #STEP_UP_MAX} 格再试。
     * 用来处理"朝下视角玩家箱压进自己脚下地面"以及"前方有矮台阶"等常见情形。
     */
    private static Vec3 tryFitOffset(Level level, Player player, AABB origBox, Vec3 nominal) {
        if (canFitOffset(level, player, origBox, nominal)) {
            return nominal;
        }
        for (double up = STEP_UP_GRAIN; up <= STEP_UP_MAX + 1.0e-6; up += STEP_UP_GRAIN) {
            Vec3 lifted = nominal.add(0.0, up, 0.0);
            if (canFitOffset(level, player, origBox, lifted)) {
                return lifted;
            }
        }
        return null;
    }

    private static boolean canFitOffset(Level level, Player player, AABB origBox, Vec3 offset) {
        AABB movedBox = origBox.move(offset.x, offset.y, offset.z);
        if (!level.getWorldBorder().isWithinBounds(movedBox)) {
            return false;
        }

        // 只把真实 collision shape 当作阻挡，忽略草、花这类可穿过的装饰方块。
        if (level.getBlockCollisions(player, movedBox).iterator().hasNext()) {
            return false;
        }
        return level.getEntityCollisions(player, movedBox).isEmpty();
    }

    /** 客户端 delta 视为"在走"的最小阈值（格²/tick²）。低于这个值就当作站立。 */
    private static final double MIN_CLIENT_DELTA_SQR = 1.0e-4;

    /**
     * 优先取玩家当前<strong>水平移动方向</strong>作为冲刺朝向（侧跑、倒退冲刺与体感一致）；
     * 没有水平位移时退化为水平视线方向；直视正上/正下时再退化为仅由偏航角决定的水平前向。
     *
     * <p>水平速度直接由客户端在按键瞬间从 {@code LocalPlayer.getDeltaMovement()} 取出
     * 并随包送上来。原因见 {@link DashRequestPayload} 注释：服务端的 deltaMovement
     * 和 xo/zo 在 vanilla 处理玩家移动包时都被 {@code absMoveTo} 重置，水平分量恒为 0。</p>
     */
    private static Vec3 horizontalDashDirection(Player player, double clientDirX, double clientDirZ) {
        Vec3 horizMove = new Vec3(clientDirX, 0.0, clientDirZ);
        if (horizMove.lengthSqr() > MIN_CLIENT_DELTA_SQR) {
            return horizMove.normalize();
        }
        Vec3 look = player.getViewVector(1.0f);
        Vec3 lookHoriz = new Vec3(look.x, 0.0, look.z);
        if (lookHoriz.lengthSqr() < 1.0e-6) {
            return horizontalFacingDirection(player);
        }
        return lookHoriz.normalize();
    }

    private static Vec3 horizontalFacingDirection(Player player) {
        double yawRad = Math.toRadians(player.getYRot());
        return new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad)).normalize();
    }

    /**
     * 消耗、音效，并施加冲刺速度脉冲；{@code targetFeet} 须已通过 {@link #findDashTarget} 扫描。
     *
     * <p>{@code targetFeet} 可能包含 step-up 抬高的 y 分量，但脉冲只取<strong>水平</strong>
     * 投影：vanilla 玩家自带 0.6 格 step-assist，矮台阶冲刺时会被自动顶上去；
     * 高于 0.6 格的台阶会被「撞墙判定」拦下，行为与玩家自己跑撞墙一致。</p>
     */
    private void applyDash(
            ServerLevel level,
            ServerPlayer player,
            ItemStack legsStack,
            Vec3 startFeet,
            Vec3 targetFeet) {
        switch (ACTIVE_DASH_MODE) {
            case MOVEMENT_DIRECTION -> applyMovementDash(level, player, legsStack, startFeet, targetFeet);
            case LOOK_DIRECTION -> applyLookDash(level, player, legsStack, targetFeet);
        }
    }

    private void applyMovementDash(
            ServerLevel level,
            ServerPlayer player,
            ItemStack legsStack,
            Vec3 startFeet,
            Vec3 targetFeet) {
        Vec3 originEye = player.getEyePosition();
        double eyeOffsetY = player.getEyeY() - player.getY();

        player.causeFoodExhaustion(MOVEMENT_DASH_HUNGER_EXHAUSTION);
        if (!player.getAbilities().instabuild) {
            // 装备在腿部槽，断裂回调要走 LEGS 槽，断裂后才能触发原版护甲损坏事件
            legsStack.hurtAndBreak(1, player, EquipmentSlot.LEGS);
        }

        level.playSound(null, originEye.x, originEye.y, originEye.z,
                SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.0f, 1.2f);

        double dx = targetFeet.x - startFeet.x;
        double dz = targetFeet.z - startFeet.z;
        double horizDistance = Math.sqrt(dx * dx + dz * dz);
        if (horizDistance < MIN_DASH_DISTANCE) {
            return;
        }
        Vec3 dashDir = new Vec3(dx / horizDistance, 0.0, dz / horizDistance);

        DashMotionTicker.start(level, player, startFeet, horizDistance, dashDir, tier.getSpeed(), originEye, eyeOffsetY);
    }

    /** 保留 main 分支旧实现：服务端直接瞬移到扫描终点。 */
    private void applyLookDash(ServerLevel level, ServerPlayer player, ItemStack legsStack, Vec3 targetFeet) {
        Vec3 originEye = player.getEyePosition();
        double eyeOffsetY = player.getEyeY() - player.getY();

        player.teleportTo(targetFeet.x, targetFeet.y, targetFeet.z);
        player.resetFallDistance();

        player.causeFoodExhaustion(LOOK_DASH_HUNGER_EXHAUSTION);
        if (!player.getAbilities().instabuild) {
            legsStack.hurtAndBreak(1, player, EquipmentSlot.LEGS);
        }

        level.playSound(null, originEye.x, originEye.y, originEye.z,
                SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.0f, 1.2f);

        Vec3 targetEye = targetFeet.add(0.0, eyeOffsetY, 0.0);
        emitTrailParticles(level, originEye, targetEye);
    }

    /** 沿轨迹播洒云粒子，强化突进的视觉反馈。 */
    static void emitTrailParticles(ServerLevel level, Vec3 from, Vec3 to) {
        double dist = from.distanceTo(to);
        int count = Math.max(4, (int) (dist * 4));
        for (int i = 0; i < count; i++) {
            double t = (double) i / count;
            Vec3 p = from.lerp(to, t);
            level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }
}
