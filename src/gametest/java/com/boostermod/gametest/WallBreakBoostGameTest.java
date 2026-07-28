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

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_noUpgradeStops", timeoutTicks = TIMEOUT)
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
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_straightTunnel", timeoutTicks = TIMEOUT)
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
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_baseDrops", timeoutTicks = TIMEOUT)
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
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_chestSemantics", timeoutTicks = TIMEOUT)
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
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wallBreak_bedrockStops", timeoutTicks = TIMEOUT)
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
                .thenSucceed();
    }

    private static void prepareCorridor(GameTestHelper helper) {
        clearLingeringFakePlayers(helper);
        for (int z = 0; z <= 16; z++) {
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
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.setOnGround(true);
        BoosterLeggingsItem.tryBoostFromKey(player, 0.0, 1.0, -1, -1);
    }

    private static int countGroundItems(GameTestHelper helper, net.minecraft.world.item.Item item) {
        AABB box = helper.getBounds().inflate(64.0);
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
        AABB box = helper.getBounds().inflate(64.0);
        List<Player> players = List.copyOf(helper.getLevel().getEntitiesOfClass(Player.class, box));
        for (Player player : players) {
            if (player instanceof net.fabricmc.fabric.api.entity.FakePlayer) {
                BoosterMotionTicker.cancel((ServerPlayer) player);
                player.discard();
            }
        }
    }
}
