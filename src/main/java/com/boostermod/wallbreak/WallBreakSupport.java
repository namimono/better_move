package com.boostermod.wallbreak;

import com.boostermod.item.BoosterEquipment;
import com.boostermod.upgrade.BoosterUpgradeHelper;
import com.boostermod.upgrade.BoosterUpgradeType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
     * 清除玩家碰撞体沿 {@code motionHint} 扫过的可破坏阻挡方块。
     * 扫掠范围内若存在不可破坏方块，则不破坏任何方块并返回 {@link Outcome#HIT_UNBREAKABLE}。
     */
    public static Outcome clearSweptPath(ServerLevel level, ServerPlayer player, Vec3 motionHint) {
        if (motionHint.lengthSqr() < 1.0e-10) {
            return Outcome.NONE;
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

        if (hitUnbreakable) {
            return Outcome.HIT_UNBREAKABLE;
        }

        for (BlockPos pos : toBreak) {
            breakWithBaseDrops(level, player, pos);
        }

        return toBreak.isEmpty() ? Outcome.NONE : Outcome.CLEARED;
    }

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
