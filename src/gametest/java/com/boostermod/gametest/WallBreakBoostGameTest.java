package com.boostermod.gametest;

import com.boostermod.BoosterMod;
import com.boostermod.item.BoosterLeggingsItem;
import com.boostermod.item.BoosterMotionTicker;
import com.boostermod.upgrade.BoosterUpgradeHelper;
import com.boostermod.upgrade.BoosterUpgradeType;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
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
