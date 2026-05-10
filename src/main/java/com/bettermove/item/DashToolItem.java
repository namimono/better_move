package com.bettermove.item;

import com.bettermove.tier.DashTier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 冲刺突进工具。手持右键沿视线方向冲刺，能走多远走多远，撞墙就停。
 *
 * <p>核心算法——「沿视线扫描玩家碰撞箱 + step-up 修正」：
 * 以 {@value #SCAN_STEP} 格步长沿视线方向平移玩家碰撞箱并检测碰撞；若发生碰撞
 * （最常见的是朝下视角时玩家箱压进自己脚下地面），沿 +y 小幅抬高最多
 * {@value #STEP_UP_MAX} 格再试，让玩家"贴着地面斜坡前进"。这样朝下视角也能突进，
 * 而不是直接判失败。</p>
 *
 * <ul>
 *   <li>距离与耐久由 {@link DashTier} 决定</li>
 *   <li>所有等级冷却 3 秒（{@value #COOLDOWN_TICKS} ticks）</li>
 *   <li>每次消耗 {@value #HUNGER_COST} 点饥饿值 + 1 点耐久</li>
 *   <li>仅当玩家完全无法前进（已经被卡死）才取消冷却</li>
 * </ul>
 */
public class DashToolItem extends Item {
    private static final int COOLDOWN_TICKS = 60;
    private static final float HUNGER_COST = 2.0f;

    /** 沿视线扫描时的步长（格）。 */
    private static final double SCAN_STEP = 0.25;
    /** 命中阻挡后做二分细化的次数；总精度 ≈ SCAN_STEP / 2^REFINE_ITERS。 */
    private static final int REFINE_ITERS = 5;
    /** 沿视线方向推进距离低于该值就视为「贴脸了，没法前进」。 */
    private static final double MIN_DASH_DISTANCE = 0.1;

    /** step-up 修正的最大抬高量（格），约一个完整台阶高度。 */
    private static final double STEP_UP_MAX = 1.0;
    /** step-up 修正的试探步长。 */
    private static final double STEP_UP_GRAIN = 0.05;

    /** 饥饿值低于该值禁止突进，避免饿到掉血时还能瞎冲。 */
    private static final int MIN_FOOD_LEVEL = 6;

    private final DashTier tier;

    public DashToolItem(Properties properties, DashTier tier) {
        super(properties);
        this.tier = tier;
    }

    public DashTier getTier() {
        return tier;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        boolean creative = player.getAbilities().instabuild;
        if (!creative && player.getFoodData().getFoodLevel() < MIN_FOOD_LEVEL) {
            return InteractionResultHolder.fail(stack);
        }

        // 双端都做可行性扫描，确保客户端的预测和服务端结果一致，避免冷却条假动作。
        Vec3 targetFeet = findDashTarget(level, player);
        if (targetFeet == null) {
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide) {
            applyDash((ServerLevel) level, player, stack, hand, targetFeet);
        }

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * 沿玩家视线扫描，找出玩家碰撞箱可平移到的最远 feet 位置。
     *
     * @return 可前进的目标坐标；若沿视线方向推进 < {@value #MIN_DASH_DISTANCE} 格视为无效
     */
    private Vec3 findDashTarget(Level level, Player player) {
        Vec3 lookVec = player.getViewVector(1.0f);
        if (lookVec.lengthSqr() < 1.0e-6) {
            return null;
        }
        Vec3 dir = lookVec.normalize();

        AABB origBox = player.getBoundingBox();
        Vec3 origin = player.position();
        double maxDist = tier.getDistance();

        // bestT: 沿视线方向的标称推进距离（决定有没有"前进"）
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

        // 阻挡后二分细化沿视线方向的进度，让玩家停得更靠近墙
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

        // 用 bestT（视线方向进度）而不是 bestOffset.length 判定，避免「原地抬升」误判为成功
        if (bestT < MIN_DASH_DISTANCE) {
            return null;
        }
        return origin.add(bestOffset);
    }

    /**
     * 尝试一个理想视线位移；若与方块碰撞，沿 +y 抬高最多 {@link #STEP_UP_MAX} 格再试。
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
        return level.noCollision(player, origBox.move(offset.x, offset.y, offset.z));
    }

    /** 服务端执行实际位移、消耗与反馈。{@code targetFeet} 必须是已经验证过的合法位置。 */
    private void applyDash(ServerLevel level, Player player, ItemStack stack, InteractionHand hand, Vec3 targetFeet) {
        Vec3 originEye = player.getEyePosition();
        double eyeOffsetY = player.getEyeY() - player.getY();

        player.teleportTo(targetFeet.x, targetFeet.y, targetFeet.z);
        player.resetFallDistance();

        player.causeFoodExhaustion(HUNGER_COST);
        if (!player.getAbilities().instabuild) {
            EquipmentSlot slot = hand == InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND
                    : EquipmentSlot.OFFHAND;
            stack.hurtAndBreak(1, player, slot);
        }

        level.playSound(null, originEye.x, originEye.y, originEye.z,
                SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.0f, 1.2f);

        Vec3 targetEye = targetFeet.add(0.0, eyeOffsetY, 0.0);
        emitTrailParticles(level, originEye, targetEye);
    }

    /** 沿轨迹播洒云粒子，强化突进的视觉反馈。 */
    private static void emitTrailParticles(ServerLevel level, Vec3 from, Vec3 to) {
        double dist = from.distanceTo(to);
        int count = Math.max(4, (int) (dist * 4));
        for (int i = 0; i < count; i++) {
            double t = (double) i / count;
            Vec3 p = from.lerp(to, t);
            level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }
}
