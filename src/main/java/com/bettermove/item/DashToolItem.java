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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 冲刺突进工具。手持右键，沿视线方向短距离突进，撞墙就停。
 * <ul>
 *   <li>距离与耐久由 {@link DashTier} 决定</li>
 *   <li>所有等级冷却 3 秒（{@value #COOLDOWN_TICKS} ticks）</li>
 *   <li>每次消耗约 {@value #HUNGER_COST} 点饥饿值 + 1 点耐久</li>
 *   <li>raycast 找到不穿墙的最远视线落点，再做玩家碰撞箱安全回退</li>
 * </ul>
 */
public class DashToolItem extends Item {
    private static final int COOLDOWN_TICKS = 60;
    private static final float HUNGER_COST = 2.0f;

    /** raycast 命中方块时，先沿视线反方向退这么多，避免目标点贴在方块表面。 */
    private static final double SAFETY_BACK_OFF = 0.4;
    /** 玩家碰撞箱仍在方块里时，每次沿视线反方向回退的步长。 */
    private static final double STEP_BACK = 0.5;
    /** 最多回退多少次仍找不到安全位置就放弃突进。 */
    private static final int MAX_RETRIES = 6;
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

        boolean dashed = true;
        if (!level.isClientSide) {
            dashed = performDash((ServerLevel) level, player, stack, hand);
        }

        if (dashed) {
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            player.swing(hand, true);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.fail(stack);
    }

    /**
     * 服务端执行实际突进。
     *
     * @return 若找到了合法目标位置并完成传送返回 {@code true}；周围全堵死等情况返回 {@code false}
     */
    private boolean performDash(ServerLevel level, Player player, ItemStack stack, InteractionHand hand) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0f);
        Vec3 endEyePos = eyePos.add(lookVec.scale(tier.getDistance()));

        BlockHitResult hit = level.clip(new ClipContext(
                eyePos, endEyePos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        Vec3 targetEye = hit.getType() == HitResult.Type.MISS
                ? endEyePos
                : hit.getLocation().subtract(lookVec.scale(SAFETY_BACK_OFF));

        double eyeOffsetY = player.getEyeY() - player.getY();
        Vec3 targetFeet = new Vec3(targetEye.x, targetEye.y - eyeOffsetY, targetEye.z);

        AABB targetBox = moveBoxTo(player, targetFeet);
        int retries = 0;
        while (!level.noCollision(player, targetBox) && retries < MAX_RETRIES) {
            targetFeet = targetFeet.subtract(lookVec.scale(STEP_BACK));
            targetBox = moveBoxTo(player, targetFeet);
            retries++;
        }
        if (!level.noCollision(player, targetBox)) {
            return false;
        }

        Vec3 originalFeet = player.position();
        player.teleportTo(targetFeet.x, targetFeet.y, targetFeet.z);
        player.resetFallDistance();

        player.causeFoodExhaustion(HUNGER_COST);
        if (!player.getAbilities().instabuild) {
            EquipmentSlot slot = hand == InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND
                    : EquipmentSlot.OFFHAND;
            stack.hurtAndBreak(1, player, slot);
        }

        level.playSound(null, eyePos.x, eyePos.y, eyePos.z,
                SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.0f, 1.2f);
        emitTrailParticles(level, originalFeet.add(0, eyeOffsetY, 0), targetEye);
        return true;
    }

    /** 把玩家原碰撞箱平移到给定 feet 坐标处。 */
    private static AABB moveBoxTo(Player player, Vec3 newFeet) {
        return player.getBoundingBox().move(
                newFeet.x - player.getX(),
                newFeet.y - player.getY(),
                newFeet.z - player.getZ()
        );
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
