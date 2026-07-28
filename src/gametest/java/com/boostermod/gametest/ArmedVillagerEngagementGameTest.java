package com.boostermod.gametest;

import com.boostermod.BoosterMod;
import com.boostermod.item.BoosterLeggingsItem;
import com.boostermod.villager.ArmedVillagerCombat;
import com.boostermod.villager.ArmedVillagerSettings;
import com.boostermod.villager.ArmedVillagerTargetMode;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 武装村民交战生命周期：通过公开目标、位置与装备槽验收。
 * 8×8 空结构外侧有屏障；需要远距离时先打通走廊并铺地板。
 */
public class ArmedVillagerEngagementGameTest {
    private static final BlockPos VILLAGER_POS = new BlockPos(3, 1, 2);
    private static final int TIMEOUT = 400;

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedEngagement_lockNearest", timeoutTicks = TIMEOUT)
    public void armedVillagerLocksNearestVisibleSurvivalPlayer(GameTestHelper helper) {
        ensureHostileDifficulty(helper);
        prepareNearArena(helper);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS);
        Player near = spawnAttackablePlayer(helper, new BlockPos(3, 1, 5), "near");
        Player far = spawnAttackablePlayer(helper, new BlockPos(5, 1, 5), "far");

        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getTarget() == near, "应锁敌最近可见可攻击玩家");
            helper.assertTrue(villager.getTarget() != far, "不得锁更远的玩家");
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedEngagement_missingGear", timeoutTicks = TIMEOUT)
    public void missingGearDoesNotEngage(GameTestHelper helper) {
        ensureHostileDifficulty(helper);
        prepareNearArena(helper);
        Villager villager = spawnAdultVillager(helper, VILLAGER_POS);
        villager.setItemSlot(EquipmentSlot.LEGS, new ItemStack(BoosterMod.BOOSTER_LEGGINGS_IRON));
        spawnAttackablePlayer(helper, new BlockPos(3, 1, 5), "target");

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(villager.getTarget() == null, "仅有推进器时不得锁敌");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedEngagement_creative", timeoutTicks = TIMEOUT)
    public void creativePlayerIsIgnored(GameTestHelper helper) {
        ensureHostileDifficulty(helper);
        prepareNearArena(helper);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS);
        spawnPlayer(helper, new BlockPos(3, 1, 5), GameType.CREATIVE, "creative");

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(villager.getTarget() == null, "创造模式玩家不得被锁敌");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedEngagement_spectator", timeoutTicks = TIMEOUT)
    public void spectatorPlayerIsIgnored(GameTestHelper helper) {
        ensureHostileDifficulty(helper);
        prepareNearArena(helper);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS);
        spawnPlayer(helper, new BlockPos(3, 1, 5), GameType.SPECTATOR, "spectator");

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(villager.getTarget() == null, "旁观模式玩家不得被锁敌");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedEngagement_peaceful", timeoutTicks = TIMEOUT)
    public void peacefulDifficultyDoesNotEngage(GameTestHelper helper) {
        setTargetMode(helper, ArmedVillagerTargetMode.PLAYERS);
        helper.getLevel().getServer().setDifficulty(Difficulty.PEACEFUL, true);
        prepareNearArena(helper);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS);
        // 不生成 FakePlayer：部分环境下玩家实体靠近会干扰村民主手物品；
        // 无目标时直接 tick 交战逻辑即可验证和平难度不锁敌。
        helper.assertTrue(
                !ArmedVillagerCombat.tickEngagement(villager),
                "和平难度交战 tick 不得接管 AI");
        helper.assertTrue(villager.getTarget() == null, "和平难度不得锁敌");

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(villager.getTarget() == null, "和平难度等待后仍不得锁敌");
            helper.assertTrue(
                    villager.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof BoosterLeggingsItem,
                    "和平难度应保留推进器");
            helper.assertTrue(
                    villager.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD),
                    "和平难度应保留剑");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedEngagement_beyondLock", timeoutTicks = TIMEOUT)
    public void playerBeyondLockRangeIsNotFirstLocked(GameTestHelper helper) {
        ensureHostileDifficulty(helper);
        prepareLongCorridor(helper, 45);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS);
        spawnAttackablePlayer(helper, new BlockPos(3, 1, 40), "out-of-lock");

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(villager.getTarget() == null, "32 格外不得首次锁敌");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedEngagement_disengageRange", timeoutTicks = TIMEOUT)
    public void targetBeyondDisengageRangeIsDropped(GameTestHelper helper) {
        ensureHostileDifficulty(helper);
        prepareLongCorridor(helper, 60);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 6), "chase-then-far");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == player, "先应锁敌"))
                .thenExecute(() -> movePlayer(helper, player, new BlockPos(3, 1, 55)))
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == null, "超过 48 格应脱离追击"))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedEngagement_removeGear", timeoutTicks = TIMEOUT)
    public void removingGearStopsEngagement(GameTestHelper helper) {
        ensureHostileDifficulty(helper);
        prepareNearArena(helper);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 5), "gear-target");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == player, "装备齐全应锁敌"))
                .thenExecute(() -> villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY))
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == null, "失去剑后应停止交战"))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedEngagement_dead", timeoutTicks = TIMEOUT)
    public void deadPlayerIsDropped(GameTestHelper helper) {
        ensureHostileDifficulty(helper);
        prepareNearArena(helper);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 5), "mortal");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == player, "应先锁敌"))
                .thenExecute(() -> {
                    player.setHealth(0.0F);
                    player.die(helper.getLevel().damageSources().genericKill());
                    if (player.isAlive()) {
                        player.discard();
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == null, "目标死亡后应脱离"))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedEngagement_lost200", timeoutTicks = 500)
    public void unreachableInvisibleTargetDisengagesAfter200Ticks(GameTestHelper helper) {
        ensureHostileDifficulty(helper);
        prepareNearArena(helper);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 5), "walled");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == player, "应先锁敌"))
                .thenExecute(() -> {
                    BlockPos box = new BlockPos(3, 1, 5);
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = 0; dy <= 2; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                if (dx == 0 && dy == 1 && dz == 0) {
                                    continue;
                                }
                                helper.setBlock(box.offset(dx, dy, dz), Blocks.STONE);
                            }
                        }
                    }
                    movePlayer(helper, player, box);
                })
                .thenIdle(ArmedVillagerCombat.LOST_APPROACH_TICKS + 20)
                .thenExecute(() -> helper.assertTrue(villager.getTarget() == null, "连续不可见且无法接近后应脱离"))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedEngagement_briefLos", timeoutTicks = TIMEOUT)
    public void briefLosLossDoesNotImmediatelyDisengage(GameTestHelper helper) {
        ensureHostileDifficulty(helper);
        prepareNearArena(helper);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 6), "blink");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == player, "应先锁敌"))
                .thenExecute(() -> {
                    helper.setBlock(new BlockPos(3, 1, 4), Blocks.STONE);
                    helper.setBlock(new BlockPos(3, 2, 4), Blocks.STONE);
                    helper.setBlock(new BlockPos(3, 3, 4), Blocks.STONE);
                })
                .thenIdle(40)
                .thenExecute(() -> helper.assertTrue(villager.getTarget() == player, "短暂失去视线不得立即脱离"))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedEngagement_chase", timeoutTicks = TIMEOUT)
    public void engagedVillagerMovesTowardTarget(GameTestHelper helper) {
        ensureHostileDifficulty(helper);
        prepareNearArena(helper);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 6), "chase");
        double[] startDistance = new double[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == player, "应锁敌"))
                .thenExecute(() -> startDistance[0] = villager.distanceTo(player))
                .thenIdle(60)
                .thenExecute(() -> helper.assertTrue(
                        villager.distanceTo(player) < startDistance[0] - 0.5,
                        "交战中应向目标寻路接近"))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedEngagement_independent", timeoutTicks = TIMEOUT)
    public void villagersLockIndependently(GameTestHelper helper) {
        ensureHostileDifficulty(helper);
        prepareNearArena(helper);
        Villager left = spawnArmedVillager(helper, new BlockPos(1, 1, 2));
        Villager right = spawnArmedVillager(helper, new BlockPos(6, 1, 2));
        Player leftPlayer = spawnAttackablePlayer(helper, new BlockPos(1, 1, 5), "left-p");
        Player rightPlayer = spawnAttackablePlayer(helper, new BlockPos(6, 1, 5), "right-p");

        helper.succeedWhen(() -> {
            helper.assertTrue(left.getTarget() == leftPlayer, "左侧村民应独立锁最近玩家");
            helper.assertTrue(right.getTarget() == rightPlayer, "右侧村民应独立锁最近玩家");
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedEngagement_peacefulSwitch", timeoutTicks = TIMEOUT)
    public void switchingToPeacefulStopsEngagement(GameTestHelper helper) {
        ensureHostileDifficulty(helper);
        prepareNearArena(helper);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 5), "switch");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == player, "非和平应锁敌"))
                .thenExecute(() -> helper.getLevel().getServer().setDifficulty(Difficulty.PEACEFUL, true))
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == null, "切到和平应停止交战"))
                .thenExecute(() -> helper.assertTrue(
                        villager.getItemBySlot(EquipmentSlot.LEGS).is(BoosterMod.BOOSTER_LEGGINGS_IRON),
                        "切和平不得卸装"))
                .thenSucceed();
    }

    // 使用 zz_ 前缀批次，避免在 defaultBatch 装备拾取用例之前留下怪物/模式污染。
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "zz_armedEngagement_monsterMode", timeoutTicks = TIMEOUT)
    public void monstersModeLocksNearestVisibleMonster(GameTestHelper helper) {
        ensureHostileDifficulty(helper);
        setTargetMode(helper, ArmedVillagerTargetMode.MONSTERS);
        prepareNearArena(helper);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS);
        Zombie near = spawnMonster(helper, new BlockPos(3, 1, 5));
        Zombie far = spawnMonster(helper, new BlockPos(5, 1, 5));
        spawnAttackablePlayer(helper, new BlockPos(1, 1, 5), "ignored-player");

        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(villager.getTarget() == near, "怪物模式应锁最近可见怪物");
                    helper.assertTrue(villager.getTarget() != far, "不得锁更远的怪物");
                    helper.assertTrue(!(villager.getTarget() instanceof Player), "怪物模式不得锁玩家");
                })
                .thenExecute(() -> cleanupMonsterModeFixture(helper))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "zz_armedEngagement_playersIgnoreMonster", timeoutTicks = TIMEOUT)
    public void playersModeIgnoresMonsters(GameTestHelper helper) {
        ensureHostileDifficulty(helper);
        setTargetMode(helper, ArmedVillagerTargetMode.PLAYERS);
        prepareNearArena(helper);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS);
        spawnMonster(helper, new BlockPos(3, 1, 5));

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(villager.getTarget() == null, "玩家模式不得锁怪物");
                    cleanupMonsterModeFixture(helper);
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "zz_armedEngagement_modeSwitch", timeoutTicks = TIMEOUT)
    public void switchingTargetModeDropsIncompatibleEngagement(GameTestHelper helper) {
        ensureHostileDifficulty(helper);
        setTargetMode(helper, ArmedVillagerTargetMode.PLAYERS);
        prepareNearArena(helper);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 5), "mode-switch");
        Zombie zombie = spawnMonster(helper, new BlockPos(5, 1, 5));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == player, "玩家模式应先锁玩家"))
                .thenExecute(() -> setTargetMode(helper, ArmedVillagerTargetMode.MONSTERS))
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == zombie, "切换为怪物模式后应改锁怪物"))
                .thenExecute(() -> cleanupMonsterModeFixture(helper))
                .thenSucceed();
    }

    /** 结构内近距离场地：铺石板并清出活动空间。 */
    private static void prepareNearArena(GameTestHelper helper) {
        clearLingeringFakePlayers(helper);
        for (int x = 1; x <= 6; x++) {
            for (int z = 1; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, 3, z), Blocks.AIR);
            }
        }
    }

    /** 清掉邻近残留 FakePlayer，避免跨用例污染锁敌。 */
    private static void clearLingeringFakePlayers(GameTestHelper helper) {
        AABB box = helper.getBounds().inflate(64.0);
        List<Player> players = List.copyOf(helper.getLevel().getEntitiesOfClass(Player.class, box));
        for (Player player : players) {
            if (player instanceof FakePlayer) {
                player.discard();
            }
        }
    }

    /** 打通 8×8 屏障并向前铺走廊，供 32/48 距离用例使用。 */
    private static void prepareLongCorridor(GameTestHelper helper, int lengthZ) {
        prepareNearArena(helper);
        for (int z = 0; z <= lengthZ; z++) {
            for (int x = 2; x <= 4; x++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
            // 拆掉结构南侧屏障（z=8 一带）以及延伸路径上的障碍
            for (int y = 0; y <= 8; y++) {
                helper.setBlock(new BlockPos(3, y, z), Blocks.AIR);
            }
            helper.setBlock(new BlockPos(3, 0, z), Blocks.STONE);
        }
    }

    private static void ensureHostileDifficulty(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(Difficulty.EASY, true);
        setTargetMode(helper, ArmedVillagerTargetMode.PLAYERS);
    }

    private static void setTargetMode(GameTestHelper helper, ArmedVillagerTargetMode mode) {
        var server = helper.getLevel().getServer();
        ArmedVillagerSettings settings = ArmedVillagerSettings.get(server);
        if (settings.setTargetMode(mode)) {
            ArmedVillagerCombat.clearAllEngagements(server);
        }
    }

    private static void cleanupMonsterModeFixture(GameTestHelper helper) {
        AABB box = helper.getBounds().inflate(64.0);
        for (Monster monster : List.copyOf(helper.getLevel().getEntitiesOfClass(Monster.class, box))) {
            monster.discard();
        }
        clearLingeringFakePlayers(helper);
        setTargetMode(helper, ArmedVillagerTargetMode.PLAYERS);
        helper.getLevel().getServer().setDifficulty(Difficulty.EASY, true);
    }

    private static Zombie spawnMonster(GameTestHelper helper, BlockPos relative) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, relative);
        zombie.setHealth(20.0F);
        helper.assertTrue(ArmedVillagerCombat.isAttackableMonster(zombie), "测试怪物应可攻击");
        return zombie;
    }

    private static Villager spawnArmedVillager(GameTestHelper helper, BlockPos relative) {
        Villager villager = spawnAdultVillager(helper, relative);
        villager.setItemSlot(EquipmentSlot.LEGS, new ItemStack(BoosterMod.BOOSTER_LEGGINGS_IRON));
        villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        villager.setGuaranteedDrop(EquipmentSlot.LEGS);
        villager.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        return villager;
    }

    private static Villager spawnAdultVillager(GameTestHelper helper, BlockPos relative) {
        Villager villager = helper.spawn(EntityType.VILLAGER, relative);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
        villager.setBaby(false);
        return villager;
    }

    private static Player spawnAttackablePlayer(GameTestHelper helper, BlockPos relative, String name) {
        return spawnPlayer(helper, relative, GameType.SURVIVAL, name);
    }

    private static Player spawnPlayer(GameTestHelper helper, BlockPos relative, GameType gameType, String name) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), name));
        player.setGameMode(gameType);
        Vec3 absolute = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
        player.moveTo(absolute.x, absolute.y, absolute.z, 0.0F, 0.0F);
        player.setHealth(20.0F);
        level.addNewPlayer(player);
        helper.assertTrue(player.isAlive(), "测试玩家应存活");
        boolean expectAttackable = gameType == GameType.SURVIVAL || gameType == GameType.ADVENTURE;
        helper.assertTrue(
                ArmedVillagerCombat.isAttackablePlayer(player) == expectAttackable,
                "可攻击资格应与游戏模式一致");
        return player;
    }

    private static void movePlayer(GameTestHelper helper, Player player, BlockPos relative) {
        Vec3 absolute = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
        player.moveTo(absolute.x, absolute.y, absolute.z, player.getYRot(), player.getXRot());
    }
}
