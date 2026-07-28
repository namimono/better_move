package com.boostermod.wallbreak;

import com.boostermod.item.BoosterEquipment;
import com.boostermod.upgrade.BoosterUpgradeHelper;
import com.boostermod.upgrade.BoosterUpgradeType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 破壁推进：沿碰撞体扫掠清除可破坏阻挡物，并生成无附魔基础掉落。
 */
public final class WallBreakSupport {
    /** 每次破壁生命代价（半颗心 = 1 点生命值）。 */
    public static final float HEALTH_COST = 1.0f;
    /** 持续破壁期间再次结算代价所需的实际清方 tick 数。 */
    public static final int COST_INTERVAL_TICKS = 10;

    private static final double BOX_EDGE_EPSILON = 1.0e-7;
    private static final ItemStack BASE_LOOT_TOOL = new ItemStack(Items.DIAMOND_PICKAXE);

    public enum Outcome {
        NONE,
        CLEARED,
        HIT_UNBREAKABLE
    }

    private WallBreakSupport() {}

    public static boolean isInstalled(ServerPlayer player) {
        return BoosterEquipment.find(player)
                .map(equipped -> BoosterUpgradeHelper.hasUpgrade(
                        equipped.stack(), BoosterUpgradeType.WALL_BREAK, player.registryAccess()))
                .orElse(false);
    }

    /**
     * 生存/冒险：直接扣除固定生命值（绕过护甲、抗性、受伤无敌帧），可致死。
     * 创造：不改变真实生命值，仍按同节奏触发受伤动画、音效与屏幕受击反馈（非先扣再回）。
     * 旁观：无代价、无反馈。
     *
     * @return {@code true} 若代价后玩家已死亡
     */
    public static boolean applyHealthCost(ServerPlayer player) {
        if (player.isSpectator()) {
            return false;
        }
        if (player.isCreative()) {
            // 仅反馈：不得改生命，也不得先扣再回
            playHurtFeedback(player);
            return false;
        }
        // 直接改生命值：绕过护甲、抗性与 hurt 无敌帧
        player.invulnerableTime = 0;
        float next = player.getHealth() - HEALTH_COST;
        if (next <= 0.0f) {
            player.setHealth(0.0f);
            if (!player.isDeadOrDying()) {
                player.die(player.damageSources().generic());
            }
        } else {
            player.setHealth(next);
        }
        playHurtFeedback(player);
        return !player.isAlive() || player.isDeadOrDying() || player.getHealth() <= 0.0f;
    }

    /**
     * 与生存同节奏的受伤可见/可听反馈：红闪动画、屏幕受击方向包、玩家受伤音效。
     * 不改动生命值。
     */
    private static void playHurtFeedback(ServerPlayer player) {
        player.animateHurt(0.0f);
        player.hurtMarked = true;
        // 屏幕受击倾斜（真实客户端连接；FakePlayer 走空发送）
        player.indicateDamage(0.0, 1.0);
        if (!player.level().isClientSide) {
            player.level().playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.PLAYER_HURT,
                    SoundSource.PLAYERS,
                    1.0f,
                    1.0f);
        }
    }

    /**
     * 仅探测扫掠结果，不破坏方块。用于不可破坏前瞻，避免在伤害结算路径外清方。
     */
    public static Outcome probeSweptPath(ServerLevel level, ServerPlayer player, Vec3 motionHint) {
        SweepResult sweep = sweep(level, player, motionHint);
        if (sweep == null) {
            return Outcome.NONE;
        }
        if (sweep.hitUnbreakable()) {
            return Outcome.HIT_UNBREAKABLE;
        }
        return sweep.toBreak().isEmpty() ? Outcome.NONE : Outcome.CLEARED;
    }

    /**
     * 当前碰撞体是否与可破坏阻挡方块相交（不含沿速度扫掠）。
     */
    public static boolean intersectsBreakableCollision(ServerLevel level, ServerPlayer player) {
        return hasBreakableInBox(level, player.getBoundingBox().deflate(1.0e-3));
    }

    /**
     * 沿飞行方向极短距离内是否仍有可破坏阻挡（连续墙体），用于区分空气间隔后的下一段墙。
     */
    public static boolean hasBreakableImmediatelyAhead(
            ServerLevel level, ServerPlayer player, Vec3 flight, double distance) {
        if (flight.lengthSqr() < 1.0e-10 || distance <= 0.0) {
            return false;
        }
        Vec3 hint = flight.normalize().scale(distance);
        Outcome near = probeSweptPath(level, player, hint);
        return near == Outcome.CLEARED || near == Outcome.HIT_UNBREAKABLE;
    }

    private static boolean hasBreakableInBox(ServerLevel level, AABB box) {
        int minX = floorInside(box.minX);
        int minY = floorInside(box.minY);
        int minZ = floorInside(box.minZ);
        int maxX = floorInside(box.maxX - BOX_EDGE_EPSILON);
        int maxY = floorInside(box.maxY - BOX_EDGE_EPSILON);
        int maxZ = floorInside(box.maxZ - BOX_EDGE_EPSILON);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0f) {
                        continue;
                    }
                    VoxelShape collision = state.getCollisionShape(level, pos);
                    if (collision.isEmpty()) {
                        continue;
                    }
                    VoxelShape moved = collision.move(pos.getX(), pos.getY(), pos.getZ());
                    if (Shapes.joinIsNotEmpty(moved, Shapes.create(box), BooleanOp.AND)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 清除玩家碰撞体沿 {@code motionHint} 扫过的可破坏阻挡方块。
     * 扫掠范围内若存在不可破坏方块，则不破坏任何方块并返回 {@link Outcome#HIT_UNBREAKABLE}。
     */
    public static Outcome clearSweptPath(ServerLevel level, ServerPlayer player, Vec3 motionHint) {
        SweepResult sweep = sweep(level, player, motionHint);
        if (sweep == null) {
            return Outcome.NONE;
        }
        if (sweep.hitUnbreakable()) {
            return Outcome.HIT_UNBREAKABLE;
        }

        for (BlockPos pos : sweep.toBreak()) {
            breakWithBaseDrops(level, player, pos);
        }

        return sweep.toBreak().isEmpty() ? Outcome.NONE : Outcome.CLEARED;
    }

    private static SweepResult sweep(ServerLevel level, ServerPlayer player, Vec3 motionHint) {
        if (motionHint.lengthSqr() < 1.0e-10) {
            return null;
        }

        AABB swept = player.getBoundingBox().deflate(1.0e-3).expandTowards(motionHint);
        List<BlockPos> toBreak = new ArrayList<>();
        boolean hitUnbreakable = false;

        int minX = floorInside(swept.minX);
        int minY = floorInside(swept.minY);
        int minZ = floorInside(swept.minZ);
        int maxX = floorInside(swept.maxX - BOX_EDGE_EPSILON);
        int maxY = floorInside(swept.maxY - BOX_EDGE_EPSILON);
        int maxZ = floorInside(swept.maxZ - BOX_EDGE_EPSILON);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    VoxelShape collision = state.getCollisionShape(level, pos);
                    if (collision.isEmpty()) {
                        continue;
                    }
                    VoxelShape moved = collision.move(pos.getX(), pos.getY(), pos.getZ());
                    if (!Shapes.joinIsNotEmpty(moved, Shapes.create(swept), BooleanOp.AND)) {
                        continue;
                    }
                    if (state.getDestroySpeed(level, pos) < 0.0f) {
                        hitUnbreakable = true;
                        continue;
                    }
                    toBreak.add(pos.immutable());
                }
            }
        }

        return new SweepResult(toBreak, hitUnbreakable);
    }

    private record SweepResult(List<BlockPos> toBreak, boolean hitUnbreakable) {}

    private static void breakWithBaseDrops(ServerLevel level, ServerPlayer player, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        Block.dropResources(state, level, pos, blockEntity, player, BASE_LOOT_TOOL.copy());
        level.destroyBlock(pos, false, player);
    }

    private static int floorInside(double value) {
        return (int) Math.floor(value + BOX_EDGE_EPSILON);
    }
}
