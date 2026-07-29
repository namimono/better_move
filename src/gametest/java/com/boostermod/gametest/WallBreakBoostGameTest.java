package com.boostermod.gametest;

import com.boostermod.BoosterMod;
import com.boostermod.item.BoosterLeggingsItem;
import com.boostermod.item.BoosterMotionTicker;
import com.boostermod.upgrade.BoosterUpgradeHelper;
import com.boostermod.upgrade.BoosterUpgradeType;
import com.boostermod.villager.VillagerBoostRunner;
import com.boostermod.wallbreak.WallBreakSupport;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 破壁推进：单堵直线墙体打通、掉落与不可破坏边界。
 * 主验收 seam：服务端 GameTest 世界中的位置、推进状态、方块与掉落物。
 */
public class WallBreakBoostGameTest {
    private static final int TIMEOUT = 200;
    private static final BlockPos PLAYER_POS = new BlockPos(3, 1, 2);
    private static final int WALL_Z = 5;

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_pathMotion", timeoutTicks = TIMEOUT)
    public void withoutWallBreakUpgradeBoostEndsOnWall(GameTestHelper helper) {
        prepareCorridor(helper);
        buildStoneWall(helper, WALL_Z);
        ServerPlayer player = spawnBoostingPlayer(helper, PLAYER_POS, "no-wb", false);

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(player))
                .thenWaitUntil(() -> helper.assertTrue(
                        BoosterMotionTicker.isBoosting(player),
                        "应成功发起推进"))
                .thenWaitUntil(() -> helper.assertTrue(
                        !BoosterMotionTicker.isBoosting(player),
                        "未安装破壁升级项时碰到水平墙体应结束推进"))
                .thenExecute(() -> {
                    helper.assertTrue(player.getZ() < helper.absolutePos(new BlockPos(3, 1, WALL_Z + 1)).getZ() + 0.5,
                            "未进入破壁推进时玩家不应穿过墙体");
                    helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 1, WALL_Z));
                    helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 2, WALL_Z));
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_pathMotion", timeoutTicks = TIMEOUT)
    public void withWallBreakUpgradeBreaksStraightOneByTwoTunnel(GameTestHelper helper) {
        prepareCorridor(helper);
        buildStoneWall(helper, WALL_Z);
        buildStoneWall(helper, WALL_Z + 1);
        ServerPlayer player = spawnBoostingPlayer(helper, PLAYER_POS, "wb-tunnel", true);

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(player))
                .thenWaitUntil(() -> helper.assertTrue(
                        BoosterMotionTicker.isBoosting(player),
                        "应成功发起推进"))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, WALL_Z + 2)).getZ(),
                        "安装破壁升级项后应穿过直线墙体"))
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, WALL_Z));
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 2, WALL_Z));
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, WALL_Z + 1));
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 2, WALL_Z + 1));
                    int sideStone = 0;
                    if (helper.getBlockState(new BlockPos(2, 1, WALL_Z)).is(Blocks.STONE)) {
                        sideStone++;
                    }
                    if (helper.getBlockState(new BlockPos(4, 1, WALL_Z)).is(Blocks.STONE)) {
                        sideStone++;
                    }
                    if (helper.getBlockState(new BlockPos(2, 2, WALL_Z)).is(Blocks.STONE)) {
                        sideStone++;
                    }
                    if (helper.getBlockState(new BlockPos(4, 2, WALL_Z)).is(Blocks.STONE)) {
                        sideStone++;
                    }
                    helper.assertTrue(
                            sideStone >= 2,
                            "直线破壁应保持约 1 格宽，侧墙不应被整片拆除, remainingSide=" + sideStone
                                    + " box=" + player.getBoundingBox());
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_pathMotion", timeoutTicks = TIMEOUT)
    public void wallBreakProducesUnenchantedBaseDropsWithoutHeldTool(GameTestHelper helper) {
        prepareCorridor(helper);
        // 仅中线放铁矿，侧墙用石头，便于按破坏格数核对基础掉落（每格铁矿固定 1 粗铁）
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(2, y, WALL_Z), Blocks.STONE);
            helper.setBlock(new BlockPos(3, y, WALL_Z), Blocks.IRON_ORE);
            helper.setBlock(new BlockPos(4, y, WALL_Z), Blocks.STONE);
        }
        ServerPlayer player = spawnBoostingPlayer(helper, PLAYER_POS, "wb-drops", true);
        ItemStack enchantedPick = new ItemStack(Items.DIAMOND_PICKAXE);
        var enchantments = helper.getLevel()
                .registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        enchantedPick.enchant(enchantments.getOrThrow(Enchantments.SILK_TOUCH), 1);
        enchantedPick.enchant(enchantments.getOrThrow(Enchantments.FORTUNE), 3);
        player.setItemSlot(EquipmentSlot.MAINHAND, enchantedPick);

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(player))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, WALL_Z + 1)).getZ(),
                        "应穿过铁矿墙"))
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, WALL_Z));
                    int brokenOres = 0;
                    for (int y = 1; y <= 3; y++) {
                        if (helper.getBlockState(new BlockPos(3, y, WALL_Z)).is(Blocks.AIR)) {
                            brokenOres++;
                        }
                    }
                    int rawIron = countGroundItems(helper, Items.RAW_IRON);
                    int oreBlocks = countGroundItems(helper, Items.IRON_ORE);
                    helper.assertTrue(brokenOres > 0, "应至少破坏一格铁矿");
                    helper.assertTrue(rawIron > 0, "铁矿应产生无附魔基础掉落（粗铁）");
                    helper.assertTrue(oreBlocks == 0, "不应应用精准采集，不得掉落矿石方块本身");
                    helper.assertTrue(
                            rawIron == brokenOres,
                            "不应应用时运：粗铁数应等于破坏的铁矿格数, rawIron="
                                    + rawIron + " brokenOres=" + brokenOres);
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_pathMotion", timeoutTicks = TIMEOUT)
    public void wallBreakClearsBlockEntityAndSpawnsContents(GameTestHelper helper) {
        prepareCorridor(helper);
        buildStoneWall(helper, WALL_Z);
        BlockPos chestRelative = new BlockPos(3, 1, WALL_Z);
        helper.setBlock(chestRelative, Blocks.CHEST);
        BlockPos chestAbsolute = helper.absolutePos(chestRelative);
        BlockEntity be = helper.getLevel().getBlockEntity(chestAbsolute);
        helper.assertTrue(be instanceof ChestBlockEntity, "应生成箱子方块实体");
        ((ChestBlockEntity) be).setItem(0, new ItemStack(Items.DIAMOND, 3));

        ServerPlayer player = spawnBoostingPlayer(helper, PLAYER_POS, "wb-chest", true);

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(player))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, WALL_Z + 1)).getZ(),
                        "应穿过含箱子的墙体"))
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.AIR, chestRelative);
                    helper.assertTrue(
                            helper.getLevel().getBlockEntity(chestAbsolute) == null,
                            "方块实体应被清理");
                    helper.assertTrue(countGroundItems(helper, Items.DIAMOND) >= 3, "箱内物品应掉落");
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_pathMotion", timeoutTicks = TIMEOUT)
    public void continuousWallBreakIgnoresThrustTickLimitAndKeepsForwardSpeed(GameTestHelper helper) {
        // empty 结构仅 8x8x8；极短 thrustTicks + 厚墙证明墙内不受固定时限截停并保持前向速度
        prepareCorridor(helper, 7);
        for (int z = 3; z <= 5; z++) {
            buildStoneWall(helper, z);
        }
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-long", true);

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(
                        player, new com.boostermod.balance.BoosterBalanceProfile(1.20, 0.060, 2)))
                .thenWaitUntil(() -> helper.assertTrue(
                        BoosterMotionTicker.isBoosting(player),
                        "应成功发起推进"))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, 3)).getZ(),
                        "应已进入连续墙体"))
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(
                            BoosterMotionTicker.isBoosting(player),
                            "thrustTicks=2 之后仍应保持破壁推进状态");
                    double midSpeed = player.getDeltaMovement().z;
                    helper.assertTrue(
                            midSpeed > 0.4,
                            "连续破壁应维持进入时的前向速度, midSpeed=" + midSpeed);
                    helper.assertTrue(
                            player.getZ() > helper.absolutePos(new BlockPos(3, 1, 4)).getZ(),
                            "保速下应继续深入墙体, z=" + player.getZ());
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 3));
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_pathMotion", timeoutTicks = TIMEOUT)
    public void leavingWallStopsSpeedHoldAndRestoresInertia(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        buildStoneWall(helper, 3);
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-exit", true);
        double[] airSpeed = {Double.NaN};

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(
                        player, new com.boostermod.balance.BoosterBalanceProfile(1.20, 0.0, 1)))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, 3)).getZ() + 0.6,
                        "应穿出单层墙体"))
                .thenExecute(() -> airSpeed[0] = player.getDeltaMovement().z)
                .thenIdle(3)
                .thenExecute(() -> {
                    double later = player.getDeltaMovement().z;
                    helper.assertTrue(
                            later < airSpeed[0] - 0.05,
                            "飞出墙体后应停止保速并受惯性阻力衰减, before="
                                    + airSpeed[0]
                                    + " after="
                                    + later);
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_pathMotion", timeoutTicks = TIMEOUT)
    public void sameBoostCanBreakSecondWallAfterAirGap(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        buildStoneWall(helper, 3);
        buildStoneWall(helper, 5);
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-second", true);

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(
                        player, new com.boostermod.balance.BoosterBalanceProfile(1.20, 0.060, 4)))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, 5)).getZ() + 0.5,
                        "同一次推进应在空气间隔后再突破第二堵墙"))
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 3));
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 2, 3));
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 5));
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 2, 5));
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_pathMotion", timeoutTicks = TIMEOUT)
    public void wallBreakFollowsFlightNotLookDirection(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        buildStoneWall(helper, 4);
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-look", true);

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(
                        player, new com.boostermod.balance.BoosterBalanceProfile(1.20, 0.060, 6)))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, 3)).getZ(),
                        "应接近墙体"))
                .thenExecute(() -> {
                    // 破壁期间猛然侧看，破坏方向仍应跟飞行路径
                    player.setYRot(90.0F);
                    player.setXRot(0.0F);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, 4)).getZ() + 0.5,
                        "侧看仍应沿原飞行路径穿过墙体"))
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 4));
                    helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 1, 4));
                    helper.assertBlockPresent(Blocks.STONE, new BlockPos(4, 1, 4));
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_pathMotion", timeoutTicks = TIMEOUT)
    public void diagonalWallBreakClearsSweptEdgesWithoutGettingStuck(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        // 斜向：朝 +x+z 飞入墙角，扫掠应清掉碰撞体经过的边缘格
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(4, y, 3), Blocks.STONE);
            helper.setBlock(new BlockPos(5, y, 3), Blocks.STONE);
            helper.setBlock(new BlockPos(4, y, 4), Blocks.STONE);
            helper.setBlock(new BlockPos(5, y, 4), Blocks.STONE);
        }
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(2, 1, 1), "wb-diag", true);

        helper.startSequence()
                .thenExecute(() -> {
                    player.setYRot(-45.0F);
                    player.setXRot(0.0F);
                    player.setOnGround(true);
                    Vec3 direction = new Vec3(1.0, 0.0, 1.0).normalize();
                    BoosterMotionTicker.start(
                            player.serverLevel(),
                            player,
                            direction,
                            new com.boostermod.balance.BoosterBalanceProfile(1.30, 0.060, 8),
                            player.getEyePosition(),
                            player.getEyeY() - player.getY(),
                            false,
                            true,
                            false);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(2, 1, 4)).getZ()
                                || player.getX() > helper.absolutePos(new BlockPos(5, 1, 1)).getX(),
                        "斜向破壁后应离开墙角区域, pos="
                                + player.position()
                                + " boosting="
                                + BoosterMotionTicker.isBoosting(player)))
                .thenExecute(() -> helper.assertTrue(
                        !player.horizontalCollision || BoosterMotionTicker.isBoosting(player),
                        "斜向破壁不应把玩家卡进剩余方块"))
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_pathMotion", timeoutTicks = TIMEOUT)
    public void highSpeedSweepDoesNotSkipBlocksInPath(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        // 高速下路径上的薄墙都必须被清掉，不能跳格
        buildStoneWall(helper, 3);
        buildStoneWall(helper, 4);
        buildStoneWall(helper, 5);
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-fast", true);

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(
                        player, new com.boostermod.balance.BoosterBalanceProfile(2.50, 0.0, 1)))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, 5)).getZ() + 0.4,
                        "高速推进应穿过路径上全部薄墙"))
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 3));
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 4));
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 5));
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 2, 3));
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 2, 4));
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 2, 5));
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_pathMotion", timeoutTicks = TIMEOUT)
    public void exhaustedForwardSpeedEndsWallBreakEligibility(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        buildStoneWall(helper, 3);
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-exhaust", true);

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(
                        player, new com.boostermod.balance.BoosterBalanceProfile(0.55, 0.0, 1)))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, 3)).getZ() + 0.3,
                        "应先穿过近处墙体"))
                .thenWaitUntil(() -> helper.assertTrue(
                        !BoosterMotionTicker.isBoosting(player),
                        "前向速度耗尽后应结束破壁资格"))
                .thenExecute(() -> {
                    buildStoneWall(helper, 6);
                    // 资格结束后，即使再撞向新墙也不应破壁
                    Vec3 ahead = helper.absoluteVec(new Vec3(3.5, 1.0, 5.1));
                    player.setPos(ahead.x, ahead.y, ahead.z);
                    player.setDeltaMovement(0.0, 0.0, 1.2);
                    player.horizontalCollision = false;
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 1, 6));
                    helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 2, 6));
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_pathMotion", timeoutTicks = TIMEOUT)
    public void unbreakableBlockStopsBoostAndStays(GameTestHelper helper) {
        prepareCorridor(helper);
        for (int y = 1; y <= 3; y++) {
            for (int x = 2; x <= 4; x++) {
                helper.setBlock(new BlockPos(x, y, WALL_Z), Blocks.BEDROCK);
            }
        }
        ServerPlayer player = spawnBoostingPlayer(helper, PLAYER_POS, "wb-bedrock", true);

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(player))
                .thenWaitUntil(() -> helper.assertTrue(
                        BoosterMotionTicker.isBoosting(player),
                        "应成功发起推进"))
                .thenWaitUntil(() -> helper.assertTrue(
                        !BoosterMotionTicker.isBoosting(player),
                        "碰到不可破坏方块应终止推进"))
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.BEDROCK, new BlockPos(3, 1, WALL_Z));
                    helper.assertBlockPresent(Blocks.BEDROCK, new BlockPos(3, 2, WALL_Z));
                    helper.assertTrue(
                            player.getZ() < helper.absolutePos(new BlockPos(3, 1, WALL_Z + 1)).getZ() + 0.5,
                            "不可破坏方块不得被穿过");
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_healthLife", timeoutTicks = TIMEOUT)
    public void firstWallEntryCostsOneHealthImmediately(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        buildStoneWall(helper, 3);
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-hp-enter", true);
        player.setHealth(20.0F);

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(
                        player, new com.boostermod.balance.BoosterBalanceProfile(1.20, 0.060, 6)))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, 3)).getZ(),
                        "应进入并突破墙体"))
                .thenExecute(() -> helper.assertTrue(
                        player.getHealth() == 19.0F,
                        "首次进入墙体应立即失去 1 点生命值, health=" + player.getHealth()))
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_healthLife", timeoutTicks = TIMEOUT)
    public void continuousClearingDamagesEveryTenTicks(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        // 厚墙 + 低速：墙内停留足够多清方 tick
        for (int z = 2; z <= 6; z++) {
            buildStoneWall(helper, z);
        }
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-hp-interval", true);
        player.setHealth(20.0F);
        float[] afterFirst = {Float.NaN};

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(
                        player, new com.boostermod.balance.BoosterBalanceProfile(0.28, 0.0, 1)))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getHealth() < 20.0F && BoosterMotionTicker.isBoosting(player),
                        "应已首次破壁扣血"))
                .thenExecute(() -> afterFirst[0] = player.getHealth())
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getHealth() <= afterFirst[0] - WallBreakSupport.HEALTH_COST,
                        "持续破壁累计 " + WallBreakSupport.COST_INTERVAL_TICKS + " 清方 tick 后再扣 1 点, first="
                                + afterFirst[0]
                                + " now="
                                + player.getHealth()))
                .thenExecute(() -> helper.assertTrue(
                        player.getHealth() == afterFirst[0] - WallBreakSupport.HEALTH_COST,
                        "第二次扣血应为固定 1 点, first=" + afterFirst[0] + " now=" + player.getHealth()))
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_healthLife", timeoutTicks = TIMEOUT)
    public void airGapDoesNotAccumulateDamageAndSecondWallCostsAgain(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        // 两堵单层墙 + 空气间隔：每堵墙各扣 1，间隔内不额外累计
        buildStoneWall(helper, 3);
        buildStoneWall(helper, 5);
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-hp-air", true);
        player.setHealth(20.0F);
        float[] afterFirstWall = {Float.NaN};
        float[] midAirHealth = {Float.NaN};

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(
                        player, new com.boostermod.balance.BoosterBalanceProfile(1.20, 0.060, 4)))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, 3)).getZ() + 0.5
                                && player.getHealth() < 20.0F
                                && player.getZ() < helper.absolutePos(new BlockPos(3, 1, 5)).getZ(),
                        "应穿出第一堵墙并进入空气间隔"))
                .thenExecute(() -> {
                    afterFirstWall[0] = player.getHealth();
                    midAirHealth[0] = player.getHealth();
                    helper.assertTrue(
                            afterFirstWall[0] == 20.0F - WallBreakSupport.HEALTH_COST,
                            "第一堵墙应只扣 1 点, health=" + afterFirstWall[0]);
                })
                .thenIdle(1)
                .thenExecute(() -> helper.assertTrue(
                        player.getHealth() == midAirHealth[0]
                                || player.getZ() >= helper.absolutePos(new BlockPos(3, 1, 5)).getZ(),
                        "空气间隔不累计破壁伤害, mid="
                                + midAirHealth[0]
                                + " now="
                                + player.getHealth()))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, 5)).getZ() + 0.4,
                        "同一次推进应突破第二堵墙"))
                .thenExecute(() -> helper.assertTrue(
                        player.getHealth() == 20.0F - 2.0F * WallBreakSupport.HEALTH_COST,
                        "两堵墙应各立即结算 1 点（空气不额外累计）, health=" + player.getHealth()))
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_healthLife", timeoutTicks = TIMEOUT)
    public void wallBreakDamageIgnoresArmorResistanceAndIFrames(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        buildStoneWall(helper, 3);
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-hp-armor", true);
        player.setHealth(20.0F);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 4, false, false));
        player.invulnerableTime = 100;

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(
                        player, new com.boostermod.balance.BoosterBalanceProfile(1.20, 0.060, 6)))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, 3)).getZ(),
                        "应穿过墙体"))
                .thenExecute(() -> helper.assertTrue(
                        player.getHealth() == 19.0F,
                        "护甲、抗性与无敌帧不得减免破壁固定伤害, health=" + player.getHealth()))
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    /**
     * die() 结束会清空战斗记录，因此在致死前校验破壁伤害源与死亡消息 key。
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_healthLife", timeoutTicks = TIMEOUT)
    public void wallBreakDeathMessageUsesCustomDamageType(GameTestHelper helper) {
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-death-msg", true);
        player.setHealth(1.0F);

        helper.startSequence()
                .thenExecute(() -> {
                    var source = player.damageSources().source(WallBreakSupport.DAMAGE_TYPE);
                    helper.assertTrue(
                            source.is(WallBreakSupport.DAMAGE_TYPE),
                            "破壁伤害源应绑定自定义伤害类型");

                    Component fromSource = source.getLocalizedDeathMessage(player);
                    helper.assertTrue(
                            fromSource.getContents() instanceof TranslatableContents contents
                                    && "death.attack.boostermod.wall_break".equals(contents.getKey()),
                            "破壁伤害源应生成专属死亡消息 key, got=" + fromSource);

                    // die() 播报取自战斗记录；在生命归零前记录并读取
                    player.getCombatTracker().recordDamage(source, WallBreakSupport.HEALTH_COST);
                    Component fromCombat = player.getCombatTracker().getDeathMessage();
                    helper.assertTrue(
                            fromCombat.getContents() instanceof TranslatableContents combatContents
                                    && "death.attack.boostermod.wall_break".equals(combatContents.getKey()),
                            "战斗记录应产出破壁死亡消息, got=" + fromCombat);

                    helper.assertTrue(
                            WallBreakSupport.applyHealthCost(player),
                            "1 点破壁代价应可致死");
                    helper.assertTrue(
                            !player.isAlive() || player.isDeadOrDying() || player.getHealth() <= 0.0F,
                            "applyHealthCost 致死后玩家应死亡");
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_healthLife", timeoutTicks = TIMEOUT)
    public void wallBreakDamageCanKillAndClearsBoostState(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        for (int z = 3; z <= 5; z++) {
            buildStoneWall(helper, z);
        }
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-hp-kill", true);
        player.setHealth(1.0F);

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(
                        player, new com.boostermod.balance.BoosterBalanceProfile(1.20, 0.060, 8)))
                .thenWaitUntil(() -> helper.assertTrue(
                        !player.isAlive() || player.isDeadOrDying() || player.getHealth() <= 0.0F,
                        "破壁伤害应可致死"))
                .thenExecute(() -> helper.assertTrue(
                        !BoosterMotionTicker.isBoosting(player),
                        "死亡后不得残留推进/破壁状态"))
                .thenIdle(6)
                .thenExecute(() -> {
                    helper.assertTrue(
                            !BoosterMotionTicker.isBoosting(player),
                            "死亡后推进状态仍应保持清理");
                    // 1 点血进入首堵墙即死，更远墙体不得继续被清
                    helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 1, 5));
                    helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 2, 5));
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_healthLife", timeoutTicks = TIMEOUT)
    public void removingBoosterMidBoostClearsWallBreak(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        for (int z = 3; z <= 5; z++) {
            buildStoneWall(helper, z);
        }
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-unequip", true);

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(
                        player, new com.boostermod.balance.BoosterBalanceProfile(1.20, 0.060, 10)))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, 3)).getZ()
                                && helper.getBlockState(new BlockPos(3, 1, 3)).is(Blocks.AIR),
                        "应先开始破壁"))
                .thenExecute(() -> player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY))
                .thenIdle(8)
                .thenExecute(() -> {
                    helper.assertTrue(
                            !WallBreakSupport.isInstalled(player),
                            "卸下推进器后统一查询应不再识别破壁升级项");
                    // 卸装后失去破壁资格：更远墙体应保留
                    helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 1, 5));
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_healthLife", timeoutTicks = TIMEOUT)
    public void removingWallBreakUpgradeMidBoostStopsClearing(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        for (int z = 3; z <= 5; z++) {
            buildStoneWall(helper, z);
        }
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-rm-upg", true);

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(
                        player, new com.boostermod.balance.BoosterBalanceProfile(1.20, 0.060, 10)))
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getBlockState(new BlockPos(3, 1, 3)).is(Blocks.AIR),
                        "应先破开近处墙体"))
                .thenExecute(() -> {
                    ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
                    SimpleContainer empty = new SimpleContainer(BoosterUpgradeHelper.MAX_SLOTS);
                    BoosterUpgradeHelper.saveContainer(legs, empty, 5, helper.getLevel().registryAccess());
                    player.setItemSlot(EquipmentSlot.LEGS, legs);
                    helper.assertTrue(
                            !BoosterUpgradeHelper.hasUpgrade(
                                    legs, BoosterUpgradeType.WALL_BREAK, helper.getLevel().registryAccess()),
                            "升级项应已从推进器移除");
                })
                .thenIdle(8)
                .thenExecute(() -> helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 1, 5)))
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_healthLife", timeoutTicks = TIMEOUT)
    public void cancelBoostClearsWallBreakState(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        buildStoneWall(helper, 3);
        buildStoneWall(helper, 5);
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-cancel", true);

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(
                        player, new com.boostermod.balance.BoosterBalanceProfile(1.20, 0.060, 10)))
                .thenWaitUntil(() -> helper.assertTrue(
                        BoosterMotionTicker.isBoosting(player),
                        "应处于推进中"))
                .thenExecute(() -> BoosterMotionTicker.cancel(player))
                .thenExecute(() -> helper.assertTrue(
                        !BoosterMotionTicker.isBoosting(player),
                        "取消推进应清除运动与破壁状态"))
                .thenIdle(6)
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 1, 5));
                    helper.assertTrue(
                            !BoosterMotionTicker.isBoosting(player),
                            "取消后不得残留破壁推进");
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_healthLife", timeoutTicks = TIMEOUT)
    public void legsSlotEnablesWallBreakViaUnifiedEquipmentQuery(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        buildStoneWall(helper, 3);
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-legs-eq", true);

        helper.startSequence()
                .thenExecute(() -> helper.assertTrue(
                        WallBreakSupport.isInstalled(player),
                        "护腿槽推进器应被统一装备查询识别为已装破壁升级项"))
                .thenExecute(() -> triggerForwardBoost(
                        player, new com.boostermod.balance.BoosterBalanceProfile(1.20, 0.060, 6)))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, 3)).getZ() + 0.4,
                        "护腿槽路径应能启用破壁推进"))
                .thenExecute(() -> helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 3)))
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_healthLife", timeoutTicks = TIMEOUT)
    public void adventureModeAlsoPaysWallBreakHealthCost(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        buildStoneWall(helper, 3);
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-adv", true);
        player.setGameMode(GameType.ADVENTURE);
        player.setHealth(20.0F);

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(
                        player, new com.boostermod.balance.BoosterBalanceProfile(1.20, 0.060, 6)))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, 3)).getZ(),
                        "冒险模式也应能破壁"))
                .thenExecute(() -> helper.assertTrue(
                        player.getHealth() == 19.0F,
                        "冒险模式应真实扣血, health=" + player.getHealth()))
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_healthLife", timeoutTicks = TIMEOUT)
    public void armedVillagerIgnoresWallBreakUpgrade(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 1));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
        villager.setBaby(false);

        ItemStack booster = new ItemStack(BoosterMod.BOOSTER_LEGGINGS_IRON);
        SimpleContainer upgrades = new SimpleContainer(BoosterUpgradeHelper.MAX_SLOTS);
        upgrades.setItem(0, new ItemStack(BoosterMod.WALL_BREAK_UPGRADE));
        BoosterUpgradeHelper.saveContainer(
                booster, upgrades, 3, helper.getLevel().registryAccess());
        villager.setItemSlot(EquipmentSlot.LEGS, booster);
        villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        villager.setGuaranteedDrop(EquipmentSlot.LEGS);
        villager.setGuaranteedDrop(EquipmentSlot.MAINHAND);

        // 距离需 >= VillagerBoostRunner.MIN_BOOST_DISTANCE（6）
        ServerPlayer target = spawnBoostingPlayer(helper, new BlockPos(3, 1, 7), "wb-vill-tgt", false);
        helper.startSequence()
                .thenExecute(() -> {
                    Vec3 vPos = helper.absoluteVec(new Vec3(3.5, 1.0, 0.5));
                    Vec3 tPos = helper.absoluteVec(new Vec3(3.5, 1.0, 7.5));
                    villager.teleportTo(vPos.x, vPos.y, vPos.z);
                    villager.setOnGround(true);
                    target.teleportTo(tPos.x, tPos.y, tPos.z);
                    helper.assertTrue(
                            villager.distanceTo(target) >= VillagerBoostRunner.MIN_BOOST_DISTANCE,
                            "测试距离应满足村民推进最小距离, d=" + villager.distanceTo(target));
                    helper.assertTrue(
                            VillagerBoostRunner.tryStartBoost(villager, target),
                            "武装村民应能按原规则发起村民推进（忽略破壁升级项）");
                })
                .thenExecute(() -> buildStoneWall(helper, 3))
                .thenWaitUntil(() -> helper.assertTrue(
                        !VillagerBoostRunner.isBoosting(villager),
                        "撞墙后村民推进应按原边界结束"))
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 1, 3));
                    helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 2, 3));
                })
                .thenExecute(() -> {
                    VillagerBoostRunner.clear(villager);
                    cleanupPlayer(target);
                    villager.discard();
                })
                .thenSucceed();
    }

    // --- issue 05: 创造反馈与玩法边界 ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_creativeBoundary", timeoutTicks = TIMEOUT)
    public void creativeWallBreakKeepsHealthAndPlaysHurtFeedback(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        buildStoneWall(helper, 3);
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-cre-fb", true);
        player.setGameMode(GameType.CREATIVE);
        player.setHealth(20.0F);
        player.hurtTime = 0;
        player.hurtDuration = 0;

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(
                        player, new com.boostermod.balance.BoosterBalanceProfile(1.20, 0.060, 6)))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, 3)).getZ()
                                && helper.getBlockState(new BlockPos(3, 1, 3)).is(Blocks.AIR),
                        "创造模式也应能破壁"))
                .thenExecute(() -> {
                    helper.assertTrue(
                            player.getHealth() == 20.0F,
                            "创造破壁不得改变真实生命值, health=" + player.getHealth());
                    helper.assertTrue(
                            player.hurtTime > 0 && player.hurtDuration > 0,
                            "创造破壁应触发受伤动画反馈, hurtTime="
                                    + player.hurtTime
                                    + " hurtDuration="
                                    + player.hurtDuration);
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_creativeBoundary", timeoutTicks = TIMEOUT)
    public void creativeFeedbackRepeatsEveryTenClearTicksWithoutHpChange(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        for (int z = 2; z <= 6; z++) {
            buildStoneWall(helper, z);
        }
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-cre-int", true);
        player.setGameMode(GameType.CREATIVE);
        player.setHealth(20.0F);
        player.hurtTime = 0;
        player.hurtDuration = 0;
        boolean[] sawSecondPulse = {false};

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(
                        player, new com.boostermod.balance.BoosterBalanceProfile(0.28, 0.0, 1)))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.hurtTime > 0 && BoosterMotionTicker.isBoosting(player),
                        "首次进入墙体应已有受伤反馈"))
                .thenExecute(() -> {
                    helper.assertTrue(
                            player.getHealth() == 20.0F,
                            "首次创造反馈后生命值仍为满, health=" + player.getHealth());
                    // 清掉本段反馈，观察下一轮 10 清方 tick 是否再次置位
                    player.hurtTime = 0;
                    player.hurtDuration = 0;
                })
                .thenWaitUntil(() -> {
                    if (player.hurtTime > 0 && player.getHealth() == 20.0F) {
                        sawSecondPulse[0] = true;
                    }
                    helper.assertTrue(
                            sawSecondPulse[0],
                            "持续破壁累计 "
                                    + WallBreakSupport.COST_INTERVAL_TICKS
                                    + " 清方 tick 后应再次受伤反馈且生命不变, hurtTime="
                                    + player.hurtTime
                                    + " health="
                                    + player.getHealth());
                })
                .thenExecute(() -> helper.assertTrue(
                        player.getHealth() == 20.0F,
                        "持续创造破壁全程生命值不变, health=" + player.getHealth()))
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_creativeBoundary", timeoutTicks = TIMEOUT)
    public void creativeFeedbackDoesNotFakeDamageThenHeal(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        buildStoneWall(helper, 3);
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-cre-no-fake", true);
        player.setGameMode(GameType.CREATIVE);
        player.setHealth(20.0F);

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(
                        player, new com.boostermod.balance.BoosterBalanceProfile(1.20, 0.060, 6)))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.hurtTime > 0
                                && player.getZ() > helper.absolutePos(new BlockPos(3, 1, 3)).getZ(),
                        "应破壁并产生反馈"))
                .thenExecute(() -> {
                    helper.assertTrue(
                            player.getHealth() == 20.0F,
                            "创造反馈不得先扣血, health=" + player.getHealth());
                    // 切回生存：若曾先扣再回，残留残血会暴露；正确实现应仍满血
                    player.setGameMode(GameType.SURVIVAL);
                    helper.assertTrue(
                            player.getHealth() == 20.0F,
                            "切回生存后生命值不得因创造假伤残留错误状态, health=" + player.getHealth());
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_creativeBoundary", timeoutTicks = TIMEOUT)
    public void wallBreakDoesNotExtraSpendDurabilityOrHunger(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        for (int z = 2; z <= 5; z++) {
            buildStoneWall(helper, z);
        }
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-res", true);
        player.setHealth(20.0F);
        player.getFoodData().setFoodLevel(20);
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        int damageBefore = legs.getDamageValue();
        float foodBefore = player.getFoodData().getFoodLevel();
        float[] exhaustAfterStart = {Float.NaN};
        int[] damageAfterStart = {-1};

        helper.startSequence()
                // 走完整推进入口，会结算一次耐久与饥饿
                .thenExecute(() -> BoosterLeggingsItem.tryBoostFromKey(player, 0.0, 1.0, -1, -1))
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(
                            BoosterMotionTicker.isBoosting(player),
                            "应已发起推进");
                    exhaustAfterStart[0] = player.getFoodData().getExhaustionLevel();
                    damageAfterStart[0] = player.getItemBySlot(EquipmentSlot.LEGS).getDamageValue();
                    helper.assertTrue(
                            damageAfterStart[0] == damageBefore + 1,
                            "推进开始应只消耗 1 点耐久, before="
                                    + damageBefore
                                    + " after="
                                    + damageAfterStart[0]);
                    helper.assertTrue(
                            player.getFoodData().getFoodLevel() == foodBefore,
                            "推进开始后饥饿值等级应不变（仅 exhaustion）");
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, 5)).getZ() + 0.3
                                && helper.getBlockState(new BlockPos(3, 1, 5)).is(Blocks.AIR),
                        "应打穿多格墙体"))
                .thenExecute(() -> {
                    int damageAfter = player.getItemBySlot(EquipmentSlot.LEGS).getDamageValue();
                    float exhaustAfter = player.getFoodData().getExhaustionLevel();
                    helper.assertTrue(
                            damageAfter == damageAfterStart[0],
                            "破壁清方不得额外消耗推进器耐久, start="
                                    + damageAfterStart[0]
                                    + " now="
                                    + damageAfter);
                    helper.assertTrue(
                            exhaustAfter == exhaustAfterStart[0]
                                    || Math.abs(exhaustAfter - exhaustAfterStart[0]) < 1.0e-4f,
                            "破壁时长不得额外增加饥饿 exhaustion, start="
                                    + exhaustAfterStart[0]
                                    + " now="
                                    + exhaustAfter);
                    helper.assertTrue(
                            player.getFoodData().getFoodLevel() == foodBefore,
                            "破壁不得额外降低饥饿值等级");
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_creativeBoundary", timeoutTicks = TIMEOUT)
    public void wallBreakDoesNotDealBodyCollisionDamageToEntities(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        buildStoneWall(helper, 3);
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-ent", true);
        // 墙后路径上的实体：破壁推进穿过时不得自动造成碰撞伤害
        net.minecraft.world.entity.animal.Pig pig =
                helper.spawn(EntityType.PIG, new BlockPos(3, 1, 5));
        pig.setHealth(10.0F);
        float pigHealthBefore = pig.getHealth();

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(
                        player, new com.boostermod.balance.BoosterBalanceProfile(1.20, 0.080, 8)))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, 4)).getZ(),
                        "玩家应突破墙体并到达实体附近"))
                .thenIdle(6)
                .thenExecute(() -> helper.assertTrue(
                        pig.isAlive() && pig.getHealth() == pigHealthBefore,
                        "普通破壁推进撞实体不得自动造成碰撞伤害, pigHealth="
                                + pig.getHealth()
                                + " before="
                                + pigHealthBefore))
                .thenExecute(() -> {
                    pig.discard();
                    cleanupPlayer(player);
                })
                .thenSucceed();
    }

    /**
     * 回归：破壁 + 过载同时启用时，蓄力达过载应能持续破壁推进（不应在首撞后立刻结束）。
     * 对应玩家症状：未过载可一直破壁；过载后反而不能一直推进。
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_overloadCombo", timeoutTicks = TIMEOUT)
    public void overloadedWithWallBreakContinuesThroughThickWall(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        for (int z = 3; z <= 5; z++) {
            buildStoneWall(helper, z);
        }
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-ovl-combo", true);
        player.setHealth(20.0F);

        helper.startSequence()
                .thenExecute(() -> startOverloadedBoost(player, 2))
                .thenWaitUntil(() -> helper.assertTrue(
                        BoosterMotionTicker.isBoosting(player),
                        "应成功发起过载+破壁推进"))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, 3)).getZ(),
                        "应已进入连续墙体（过载不得在首撞立刻结束推进）"))
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(
                            BoosterMotionTicker.isBoosting(player),
                            "过载+破壁应在 thrustTicks 结束后仍保持破壁推进, boosting="
                                    + BoosterMotionTicker.isBoosting(player)
                                    + " z=" + player.getZ());
                    helper.assertTrue(
                            player.getZ() > helper.absolutePos(new BlockPos(3, 1, 4)).getZ(),
                            "过载+破壁应持续深入厚墙, z=" + player.getZ());
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 3));
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 4));
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    /**
     * 对照：未过载 + 破壁在同样厚墙下可持续破壁（证明问题专属于过载组合）。
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_overloadCombo", timeoutTicks = TIMEOUT)
    public void nonOverloadedWithWallBreakContinuesThroughThickWall(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        for (int z = 3; z <= 5; z++) {
            buildStoneWall(helper, z);
        }
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-no-ovl", true);

        helper.startSequence()
                .thenExecute(() -> triggerForwardBoost(
                        player, new com.boostermod.balance.BoosterBalanceProfile(1.20, 0.060, 2)))
                .thenWaitUntil(() -> helper.assertTrue(
                        BoosterMotionTicker.isBoosting(player),
                        "应成功发起非过载破壁推进"))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, 3)).getZ(),
                        "应已进入连续墙体"))
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(
                            BoosterMotionTicker.isBoosting(player),
                            "非过载破壁应保持推进");
                    helper.assertTrue(
                            player.getZ() > helper.absolutePos(new BlockPos(3, 1, 4)).getZ(),
                            "非过载破壁应持续深入厚墙, z=" + player.getZ());
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    /**
     * 过载+破壁：进入墙体应触发真实过载爆炸自伤，并仍能继续破壁。
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_overloadCombo", timeoutTicks = TIMEOUT)
    public void overloadedWithWallBreakDetonatesAndKeepsGoing(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        for (int z = 3; z <= 5; z++) {
            buildStoneWall(helper, z);
        }
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-ovl-boom", true);
        player.setHealth(20.0F);

        helper.startSequence()
                .thenExecute(() -> startOverloadedBoost(player, 2))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, 3)).getZ(),
                        "应进入墙体"))
                .thenExecute(() -> {
                    float health = player.getHealth();
                    // 过载自伤 4 + 破壁首次代价 1 → ≤15；不得满血（说明未炸）
                    helper.assertTrue(
                            health <= 20.0F - com.boostermod.charge.OverloadExplosion.SELF_DAMAGE,
                            "进入墙体应触发过载爆炸自伤, health=" + health);
                    helper.assertTrue(
                            BoosterMotionTicker.isBoosting(player)
                                    || player.getZ()
                                            > helper.absolutePos(new BlockPos(3, 1, 4)).getZ(),
                            "爆炸后仍应保持破壁推进或已深入墙体");
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    /**
     * 过载+破壁：空气间隔隔开的两段墙体，离开后应 re-arm，第二段再炸一次。
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_overloadCombo", timeoutTicks = TIMEOUT)
    public void overloadedWithWallBreakRearmsExplosionAcrossAirGap(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        buildStoneWall(helper, 3);
        // z=4 空气间隔
        buildStoneWall(helper, 5);
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "wb-ovl-rearm", true);
        player.setHealth(20.0F);
        float[] afterFirst = {20.0F};

        helper.startSequence()
                .thenExecute(() -> startOverloadedBoost(player, 6))
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, 3)).getZ() + 0.4,
                        "应穿过第一堵墙"))
                .thenExecute(() -> {
                    afterFirst[0] = player.getHealth();
                    helper.assertTrue(
                            afterFirst[0]
                                    <= 20.0F - com.boostermod.charge.OverloadExplosion.SELF_DAMAGE,
                            "第一堵墙应已过载爆炸, health=" + afterFirst[0]);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        player.getZ() > helper.absolutePos(new BlockPos(3, 1, 5)).getZ(),
                        "应进入第二堵墙"))
                .thenExecute(() -> {
                    float health = player.getHealth();
                    helper.assertTrue(
                            health
                                    <= afterFirst[0]
                                            - com.boostermod.charge.OverloadExplosion.SELF_DAMAGE
                                            + 0.01F,
                            "空气间隔后第二段墙应再次过载爆炸, afterFirst="
                                    + afterFirst[0]
                                    + " health="
                                    + health);
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 3));
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 5));
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    /**
     * 无破壁时过载仍保持「首次撞击爆炸并结束」，不得误开持续破壁。
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_overloadCombo", timeoutTicks = TIMEOUT)
    public void overloadedWithoutWallBreakStillEndsOnFirstImpact(GameTestHelper helper) {
        prepareCorridor(helper, 7);
        buildStoneWall(helper, 3);
        ServerPlayer player = spawnBoostingPlayer(helper, new BlockPos(3, 1, 1), "ovl-no-wb", false);
        player.setHealth(20.0F);

        helper.startSequence()
                .thenExecute(() -> startOverloadedBoost(player, 6))
                .thenWaitUntil(() -> helper.assertTrue(
                        !BoosterMotionTicker.isBoosting(player),
                        "无破壁的过载应在首次撞击后结束推进"))
                .thenExecute(() -> {
                    helper.assertTrue(
                            player.getZ()
                                    < helper.absolutePos(new BlockPos(3, 1, 4)).getZ(),
                            "无破壁过载不得穿过厚墙后半段, z=" + player.getZ());
                    // 墙体可能被爆炸轰开一部分，但推进本身应已结束
                    helper.assertTrue(
                            player.getHealth()
                                    <= 20.0F - com.boostermod.charge.OverloadExplosion.SELF_DAMAGE
                                            + 0.01F,
                            "应发生过载自伤, health=" + player.getHealth());
                })
                .thenExecute(() -> cleanupPlayer(player))
                .thenSucceed();
    }

    private static void startOverloadedBoost(ServerPlayer player, int thrustTicks) {
        Vec3 direction = new Vec3(0.0, 0.0, 1.0);
        BoosterMotionTicker.start(
                player.serverLevel(),
                player,
                direction,
                new com.boostermod.balance.BoosterBalanceProfile(1.20, 0.060, thrustTicks),
                player.getEyePosition(),
                player.getEyeY() - player.getY(),
                false,
                true,
                true);
    }

    private static void prepareCorridor(GameTestHelper helper) {
        // fabric empty 结构为 8x8x8；超出结构的 setBlock 会写进邻近 GameTest 场地
        prepareCorridor(helper, 7);
    }

    private static void prepareCorridor(GameTestHelper helper, int maxZ) {
        clearLingeringFakePlayers(helper);
        for (int z = 0; z <= maxZ; z++) {
            for (int x = 1; x <= 5; x++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    private static void buildStoneWall(GameTestHelper helper, int z) {
        for (int y = 1; y <= 3; y++) {
            for (int x = 2; x <= 4; x++) {
                helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
    }

    private static ServerPlayer spawnBoostingPlayer(
            GameTestHelper helper, BlockPos relative, String name, boolean withWallBreak) {
        ServerLevel level = helper.getLevel();
        PhysicsFakePlayer player = new PhysicsFakePlayer(level, new GameProfile(UUID.randomUUID(), name));
        player.setGameMode(GameType.SURVIVAL);
        // 显式落在方块中心，保证 0.6 宽碰撞体只覆盖 1 格通道
        Vec3 absolute = helper.absoluteVec(new Vec3(relative.getX() + 0.5, relative.getY(), relative.getZ() + 0.5));
        player.moveTo(absolute.x, absolute.y, absolute.z, 0.0F, 0.0F);
        player.setPos(absolute.x, absolute.y, absolute.z);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.setHealth(20.0F);
        player.getFoodData().setFoodLevel(20);
        player.setOnGround(true);
        player.invulnerableTime = 0;

        ItemStack booster = new ItemStack(BoosterMod.BOOSTER_LEGGINGS_DIAMOND);
        if (withWallBreak) {
            SimpleContainer upgrades = new SimpleContainer(BoosterUpgradeHelper.MAX_SLOTS);
            upgrades.setItem(0, new ItemStack(BoosterMod.WALL_BREAK_UPGRADE));
            BoosterUpgradeHelper.saveContainer(booster, upgrades, 5, level.registryAccess());
            helper.assertTrue(
                    BoosterUpgradeHelper.hasUpgrade(booster, BoosterUpgradeType.WALL_BREAK, level.registryAccess()),
                    "测试推进器应已安装破壁升级项");
        }
        player.setItemSlot(EquipmentSlot.LEGS, booster);
        level.addNewPlayer(player);
        return player;
    }

    private static void triggerForwardBoost(ServerPlayer player) {
        triggerForwardBoost(player, null);
    }

    private static void triggerForwardBoost(
            ServerPlayer player, com.boostermod.balance.BoosterBalanceProfile profileOverride) {
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.setOnGround(true);
        if (profileOverride == null) {
            BoosterLeggingsItem.tryBoostFromKey(player, 0.0, 1.0, -1, -1);
            return;
        }
        Vec3 direction = new Vec3(0.0, 0.0, 1.0);
        Vec3 originEye = player.getEyePosition();
        double eyeOffsetY = player.getEyeY() - player.getY();
        BoosterMotionTicker.start(
                player.serverLevel(),
                player,
                direction,
                profileOverride,
                originEye,
                eyeOffsetY,
                false,
                true,
                false);
    }

    private static void cleanupPlayer(ServerPlayer player) {
        BoosterMotionTicker.cancel(player);
        // 清掉破壁掉落，避免飘进邻近 GameTest 场地
        AABB box = player.getBoundingBox().inflate(8.0);
        for (ItemEntity item : List.copyOf(player.serverLevel().getEntitiesOfClass(ItemEntity.class, box))) {
            item.discard();
        }
        player.discard();
    }

    private static int countGroundItems(GameTestHelper helper, net.minecraft.world.item.Item item) {
        AABB box = helper.getBounds().inflate(8.0);
        List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, box);
        int count = 0;
        for (ItemEntity entity : items) {
            if (entity.getItem().is(item)) {
                count += entity.getItem().getCount();
            }
        }
        return count;
    }

    private static void clearLingeringFakePlayers(GameTestHelper helper) {
        AABB box = helper.getBounds().inflate(8.0);
        List<Player> players = List.copyOf(helper.getLevel().getEntitiesOfClass(Player.class, box));
        for (Player player : players) {
            if (player instanceof net.fabricmc.fabric.api.entity.FakePlayer) {
                BoosterMotionTicker.cancel((ServerPlayer) player);
                player.discard();
            }
        }
    }
}
