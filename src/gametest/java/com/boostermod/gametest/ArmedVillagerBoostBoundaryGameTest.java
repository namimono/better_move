package com.boostermod.gametest;

import com.boostermod.BoosterMod;
import com.boostermod.tier.BoosterTier;
import com.boostermod.upgrade.BoosterUpgradeHelper;
import com.boostermod.upgrade.BoosterUpgradeType;
import com.boostermod.villager.ArmedVillagerCombat;
import com.boostermod.villager.ArmedVillagerEquipment;
import com.boostermod.villager.VillagerBoostRunner;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 村民推进边界与装备生命周期：禁止发起、运动终止、坠落、升级项隔离、损坏与掉落。
 */
public class ArmedVillagerBoostBoundaryGameTest {
    private static final BlockPos VILLAGER_POS = new BlockPos(3, 1, 2);
    private static final int TIMEOUT = 500;

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoundary_air", timeoutTicks = TIMEOUT)
    public void airborneDoesNotBoost(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareLongCorridor(helper, 30);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.IRON_SWORD);
        // 先近距离锁敌（不足 6 格不推进），再置空并拉远目标。
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 4), "air-boost");
        int[] legsBefore = new int[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == player, "应锁敌"))
                .thenExecute(() -> {
                    // 清掉脚下整段走廊，避免落到相邻地板；测试期暂关重力保持离地。
                    for (int x = 2; x <= 4; x++) {
                        for (int z = 1; z <= 3; z++) {
                            for (int y = 0; y <= 2; y++) {
                                helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                            }
                        }
                    }
                    Vec3 feet = helper.absoluteVec(Vec3.atBottomCenterOf(VILLAGER_POS));
                    villager.teleportTo(feet.x, feet.y + 4.0, feet.z);
                    villager.setDeltaMovement(0.0, 0.0, 0.0);
                    villager.setOnGround(false);
                    villager.setNoGravity(true);
                    movePlayer(helper, player, new BlockPos(3, 1, 14));
                    legsBefore[0] = villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue();
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    helper.assertTrue(!villager.onGround(), "村民应仍在空中");
                    helper.assertTrue(!VillagerBoostRunner.isBoosting(villager), "空中不得发起村民推进");
                    helper.assertValueEqual(
                            villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue(),
                            legsBefore[0],
                            "空中不得消耗推进器");
                    villager.setNoGravity(false);
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoundary_water", timeoutTicks = TIMEOUT)
    public void inWaterDoesNotBoost(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareLongCorridor(helper, 30);
        for (int z = 1; z <= 4; z++) {
            helper.setBlock(new BlockPos(3, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(3, 1, z), Blocks.WATER);
        }
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.IRON_SWORD);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 14), "water-boost");
        int legsBefore = villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == player, "应锁敌"))
                .thenIdle(30)
                .thenExecute(() -> {
                    helper.assertTrue(villager.isInWater() || villager.isInWaterOrBubble(), "村民应在水中");
                    helper.assertTrue(!VillagerBoostRunner.isBoosting(villager), "水中不得发起村民推进");
                    helper.assertValueEqual(
                            legsBefore,
                            villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue(),
                            "水中不得消耗推进器");
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoundary_lava", timeoutTicks = TIMEOUT)
    public void inLavaDoesNotBoost(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareLongCorridor(helper, 30);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.LAVA);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.IRON_SWORD);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 14), "lava-boost");
        int legsBefore = villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == player, "应锁敌"))
                .thenIdle(15)
                .thenExecute(() -> {
                    helper.assertTrue(villager.isInLava(), "村民应在熔岩中");
                    helper.assertTrue(!VillagerBoostRunner.isBoosting(villager), "熔岩中不得发起村民推进");
                    helper.assertValueEqual(
                            legsBefore,
                            villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue(),
                            "熔岩中不得消耗推进器");
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoundary_vehicle", timeoutTicks = TIMEOUT)
    public void passengerDoesNotBoost(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareLongCorridor(helper, 30);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.RAIL);
        Minecart cart = helper.spawn(EntityType.MINECART, VILLAGER_POS);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.IRON_SWORD);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 14), "vehicle-boost");
        int legsBefore = villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == player, "应锁敌"))
                .thenExecute(() -> helper.assertTrue(villager.startRiding(cart), "村民应登上矿车"))
                .thenIdle(30)
                .thenExecute(() -> {
                    helper.assertTrue(villager.isPassenger(), "村民应仍在载具上");
                    helper.assertTrue(!VillagerBoostRunner.isBoosting(villager), "乘坐载具不得发起村民推进");
                    helper.assertValueEqual(
                            legsBefore,
                            villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue(),
                            "载具上不得消耗推进器");
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoundary_leash", timeoutTicks = TIMEOUT)
    public void leashedDoesNotBoost(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareLongCorridor(helper, 30);
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.OAK_FENCE);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.IRON_SWORD);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 14), "leash-boost");
        int legsBefore = villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == player, "应锁敌"))
                .thenExecute(() -> {
                    var knot = net.minecraft.world.entity.decoration.LeashFenceKnotEntity.getOrCreateKnot(
                            helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 1)));
                    villager.setLeashedTo(knot, true);
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    helper.assertTrue(villager.isLeashed(), "村民应被拴住");
                    helper.assertTrue(!VillagerBoostRunner.isBoosting(villager), "拴绳状态不得发起村民推进");
                    helper.assertValueEqual(
                            legsBefore,
                            villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue(),
                            "拴绳时不得消耗推进器");
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoundary_solidEnd", timeoutTicks = TIMEOUT)
    public void boostEndsOnSolidCollision(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareLongCorridor(helper, 30);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.IRON_SWORD);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 14), "solid-end");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(VillagerBoostRunner.isBoosting(villager), "应先发起推进"))
                .thenExecute(() -> {
                    for (int x = 2; x <= 4; x++) {
                        for (int y = 1; y <= 3; y++) {
                            helper.setBlock(new BlockPos(x, y, 5), Blocks.STONE);
                        }
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        !VillagerBoostRunner.isBoosting(villager),
                        "碰到固体障碍后推进应立即结束"))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoundary_waterEnd", timeoutTicks = TIMEOUT)
    public void boostEndsOnEnteringWater(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareLongCorridor(helper, 30);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.IRON_SWORD);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 14), "water-end");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(VillagerBoostRunner.isBoosting(villager), "应先发起推进"))
                .thenExecute(() -> {
                    for (int z = 4; z <= 8; z++) {
                        helper.setBlock(new BlockPos(3, 1, z), Blocks.WATER);
                    }
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(villager.isInWater() || villager.isInWaterOrBubble(), "村民应进入水中");
                    helper.assertTrue(!VillagerBoostRunner.isBoosting(villager), "进入水后推进应立即结束");
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoundary_lavaEnd", timeoutTicks = TIMEOUT)
    public void boostEndsOnEnteringLava(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareLongCorridor(helper, 30);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.IRON_SWORD);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 14), "lava-end");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(VillagerBoostRunner.isBoosting(villager), "应先发起推进"))
                .thenExecute(() -> {
                    for (int z = 4; z <= 7; z++) {
                        helper.setBlock(new BlockPos(3, 1, z), Blocks.LAVA);
                    }
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(villager.isInLava(), "村民应进入熔岩");
                    helper.assertTrue(!VillagerBoostRunner.isBoosting(villager), "进入熔岩后推进应立即结束");
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoundary_loseGear", timeoutTicks = TIMEOUT)
    public void losingBoosterEndsBoostAndCombat(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareLongCorridor(helper, 30);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.IRON_SWORD);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 14), "lose-booster");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(VillagerBoostRunner.isBoosting(villager), "应先推进"))
                .thenExecute(() -> villager.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY))
                .thenWaitUntil(() -> {
                    helper.assertTrue(!ArmedVillagerEquipment.isArmed(villager), "失去推进器后不再武装");
                    helper.assertTrue(!VillagerBoostRunner.isBoosting(villager), "进行中的推进应结束");
                    helper.assertTrue(villager.getTarget() == null, "应停止交战并清除目标");
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoundary_deathClear", timeoutTicks = TIMEOUT)
    public void deathEndsBoostWithoutResidualState(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareLongCorridor(helper, 30);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.IRON_SWORD);
        spawnAttackablePlayer(helper, new BlockPos(3, 1, 14), "death-clear");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(VillagerBoostRunner.isBoosting(villager), "应先推进"))
                .thenExecute(() -> villager.hurt(
                        helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE))
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(
                        !VillagerBoostRunner.isBoosting(villager),
                        "死亡后不得残留推进运行状态"))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoundary_exhaust", timeoutTicks = TIMEOUT)
    public void boosterExhaustionStopsArmedCombat(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareLongCorridor(helper, 30);
        Villager villager = spawnAdultVillager(helper, VILLAGER_POS);
        ItemStack legs = new ItemStack(BoosterMod.BOOSTER_LEGGINGS_IRON);
        legs.setDamageValue(BoosterTier.IRON.getDurability() - 1);
        villager.setItemSlot(EquipmentSlot.LEGS, legs);
        villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        villager.setGuaranteedDrop(EquipmentSlot.LEGS);
        villager.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 14), "exhaust");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == player, "应先锁敌"))
                .thenWaitUntil(() -> helper.assertTrue(
                        villager.getItemBySlot(EquipmentSlot.LEGS).isEmpty(),
                        "发起推进应耗尽最后 1 点推进器耐久"))
                .thenExecute(() -> {
                    helper.assertTrue(!ArmedVillagerEquipment.isArmed(villager), "应立即退出武装状态");
                    helper.assertTrue(!VillagerBoostRunner.isBoosting(villager), "不得残留推进能力");
                    helper.assertTrue(villager.getTarget() == null, "应停止攻击");
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoundary_fall", timeoutTicks = TIMEOUT)
    public void fallDistanceClearedDuringBoostThenResumes(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareLongCorridor(helper, 30);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.IRON_SWORD);
        spawnAttackablePlayer(helper, new BlockPos(3, 1, 14), "fall-dist");
        float[] healthBeforeFall = new float[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(VillagerBoostRunner.isBoosting(villager), "应进入推进"))
                .thenExecute(() -> villager.fallDistance = 40.0F)
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(VillagerBoostRunner.isBoosting(villager), "推进应仍在进行");
                    helper.assertValueEqual(villager.fallDistance, 0.0F, "推进期间不得累计坠落距离");
                })
                .thenWaitUntil(() -> helper.assertTrue(!VillagerBoostRunner.isBoosting(villager), "推进应结束"))
                .thenExecute(() -> {
                    villager.setNoGravity(false);
                    Vec3 pos = villager.position();
                    villager.teleportTo(pos.x, pos.y + 20.0, pos.z);
                    villager.setDeltaMovement(0.0, -1.0, 0.0);
                    villager.setOnGround(false);
                    villager.fallDistance = 0.0F;
                    healthBeforeFall[0] = villager.getHealth();
                })
                .thenIdle(40)
                .thenExecute(() -> helper.assertTrue(
                        villager.fallDistance > 3.0F || villager.getHealth() < healthBeforeFall[0],
                        "推进结束后应恢复原版坠落累计"))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoundary_upgrades", timeoutTicks = TIMEOUT)
    public void installedUpgradesDoNotChangeVillagerBoost(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareLongCorridor(helper, 30);

        Villager villager = spawnAdultVillager(helper, VILLAGER_POS);
        ItemStack booster = upgradedBooster(helper);
        villager.setItemSlot(EquipmentSlot.LEGS, booster);
        villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        villager.setGuaranteedDrop(EquipmentSlot.LEGS);
        villager.setGuaranteedDrop(EquipmentSlot.MAINHAND);

        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 4), "upgrades");
        int[] afterFirst = new int[1];
        int[] legsBeforeAir = new int[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == player, "应锁敌"))
                .thenExecute(() -> {
                    for (int x = 2; x <= 4; x++) {
                        for (int z = 1; z <= 3; z++) {
                            for (int y = 0; y <= 2; y++) {
                                helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                            }
                        }
                    }
                    Vec3 feet = helper.absoluteVec(Vec3.atBottomCenterOf(VILLAGER_POS));
                    villager.teleportTo(feet.x, feet.y + 4.0, feet.z);
                    villager.setOnGround(false);
                    villager.setNoGravity(true);
                    villager.setDeltaMovement(0.0, 0.0, 0.0);
                    movePlayer(helper, player, new BlockPos(3, 1, 14));
                    legsBeforeAir[0] = villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue();
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertTrue(!villager.onGround(), "应在空中");
                    helper.assertTrue(!VillagerBoostRunner.isBoosting(villager), "空中推进升级项不得作用于村民");
                    helper.assertValueEqual(
                            villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue(),
                            legsBeforeAir[0],
                            "空中推进升级项不得让村民消耗推进器");
                    // 铺回地面并回到起点
                    for (int x = 2; x <= 4; x++) {
                        for (int z = 1; z <= 3; z++) {
                            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                        }
                    }
                    villager.setNoGravity(false);
                    Vec3 feet = helper.absoluteVec(Vec3.atBottomCenterOf(VILLAGER_POS));
                    villager.teleportTo(feet.x, feet.y, feet.z);
                    villager.setOnGround(true);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue() > legsBeforeAir[0],
                        "地面上仍应按基础规则推进一次"))
                .thenExecute(() -> afterFirst[0] = villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue())
                .thenExecute(() -> {
                    movePlayer(helper, player, new BlockPos(3, 1, 16));
                    Vec3 feet = helper.absoluteVec(Vec3.atBottomCenterOf(VILLAGER_POS));
                    villager.teleportTo(feet.x, feet.y, feet.z);
                    villager.setOnGround(true);
                })
                .thenIdle(VillagerBoostRunner.COOLDOWN_TICKS - 5)
                .thenExecute(() -> helper.assertValueEqual(
                        villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue(),
                        afterFirst[0],
                        "无冷却升级项不得取消村民推进 60 tick 冷却"))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoundary_removeSword", timeoutTicks = TIMEOUT)
    public void removingSwordStopsCombatAndAllowsRelock(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareLongCorridor(helper, 30);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.IRON_SWORD);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 10), "remove-sword");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == player, "应先交战"))
                .thenExecute(() -> villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY))
                .thenWaitUntil(() -> {
                    helper.assertTrue(!ArmedVillagerEquipment.isArmed(villager), "卸剑后不再武装");
                    helper.assertTrue(villager.getTarget() == null, "应交、剑击与推进均应停止");
                    helper.assertTrue(!VillagerBoostRunner.isBoosting(villager), "推进不得残留");
                })
                .thenExecute(() -> {
                    villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                    villager.setGuaranteedDrop(EquipmentSlot.MAINHAND);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        villager.getTarget() == player,
                        "重新满足装备条件后应可再次锁敌"))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoundary_deathDrop", timeoutTicks = TIMEOUT)
    public void deathDropsIntactBoosterAndSword(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareNearArena(helper);
        Villager villager = spawnAdultVillager(helper, VILLAGER_POS);

        ItemStack booster = upgradedBooster(helper);
        booster.setDamageValue(23);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setDamageValue(11);
        var sharpness = helper.getLevel()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS);
        sword.enchant(sharpness, 2);

        villager.setItemSlot(EquipmentSlot.LEGS, booster);
        villager.setItemSlot(EquipmentSlot.MAINHAND, sword);
        villager.setGuaranteedDrop(EquipmentSlot.LEGS);
        villager.setGuaranteedDrop(EquipmentSlot.MAINHAND);

        helper.startSequence()
                .thenExecute(() -> villager.hurt(
                        helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE))
                .thenWaitUntil(() -> {
                    ItemStack droppedBooster = findDroppedStack(helper, BoosterMod.BOOSTER_LEGGINGS_IRON);
                    ItemStack droppedSword = findDroppedStack(helper, Items.DIAMOND_SWORD);
                    helper.assertTrue(!droppedBooster.isEmpty(), "死亡应掉落推进器");
                    helper.assertTrue(!droppedSword.isEmpty(), "死亡应掉落剑");
                    helper.assertValueEqual(23, droppedBooster.getDamageValue(), "推进器耐久应保留");
                    helper.assertValueEqual(11, droppedSword.getDamageValue(), "剑耐久应保留");
                    helper.assertTrue(
                            BoosterUpgradeHelper.hasUpgrade(
                                    droppedBooster,
                                    BoosterUpgradeType.AIR_DASH,
                                    helper.getLevel().registryAccess()),
                            "升级项数据应保留");
                    helper.assertTrue(
                            droppedSword.getEnchantments().getLevel(sharpness) == 2,
                            "剑附魔应保留");
                })
                .thenSucceed();
    }

    private static ItemStack upgradedBooster(GameTestHelper helper) {
        ItemStack booster = new ItemStack(BoosterMod.BOOSTER_LEGGINGS_IRON);
        SimpleContainer upgrades = new SimpleContainer(BoosterUpgradeHelper.MAX_SLOTS);
        upgrades.setItem(0, new ItemStack(BoosterMod.AIR_DASH_UPGRADE));
        upgrades.setItem(1, new ItemStack(BoosterMod.NO_COOLDOWN_UPGRADE));
        upgrades.setItem(2, new ItemStack(BoosterMod.RANDOM_IMPULSE_UPGRADE));
        upgrades.setItem(3, new ItemStack(BoosterMod.VERTICAL_LAUNCH_UPGRADE));
        upgrades.setItem(4, new ItemStack(BoosterMod.BURROW_UPGRADE));
        upgrades.setItem(5, new ItemStack(BoosterMod.BOOST_STRIKE_UPGRADE));
        BoosterUpgradeHelper.saveContainer(
                booster, upgrades, BoosterTier.IRON.getUpgradeSlots(), helper.getLevel().registryAccess());
        return booster;
    }

    private static ItemStack findDroppedStack(GameTestHelper helper, net.minecraft.world.item.Item item) {
        AABB box = helper.getBounds().inflate(8.0);
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
            if (entity.getItem().is(item)) {
                return entity.getItem();
            }
        }
        return ItemStack.EMPTY;
    }

    private static void ensureCombatDifficulty(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
    }

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

    private static void prepareLongCorridor(GameTestHelper helper, int lengthZ) {
        prepareNearArena(helper);
        for (int z = 0; z <= lengthZ; z++) {
            for (int x = 2; x <= 4; x++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
            helper.setBlock(new BlockPos(3, 0, z), Blocks.STONE);
        }
    }

    private static void clearLingeringFakePlayers(GameTestHelper helper) {
        AABB box = helper.getBounds().inflate(64.0);
        List<Player> players = List.copyOf(helper.getLevel().getEntitiesOfClass(Player.class, box));
        for (Player player : players) {
            if (player instanceof FakePlayer) {
                player.discard();
            }
        }
    }

    private static Villager spawnArmedVillager(
            GameTestHelper helper, BlockPos relative, net.minecraft.world.item.Item swordItem) {
        Villager villager = spawnAdultVillager(helper, relative);
        villager.setItemSlot(EquipmentSlot.LEGS, new ItemStack(BoosterMod.BOOSTER_LEGGINGS_IRON));
        villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(swordItem));
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
        ServerLevel level = helper.getLevel();
        VulnerableFakePlayer player = new VulnerableFakePlayer(level, new GameProfile(UUID.randomUUID(), name));
        player.setGameMode(GameType.SURVIVAL);
        Vec3 absolute = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
        player.moveTo(absolute.x, absolute.y, absolute.z, 0.0F, 0.0F);
        player.setHealth(20.0F);
        player.invulnerableTime = 0;
        level.addNewPlayer(player);
        helper.assertTrue(ArmedVillagerCombat.isAttackablePlayer(player), "测试玩家应可攻击");
        return player;
    }

    private static void movePlayer(GameTestHelper helper, Player player, BlockPos relative) {
        Vec3 absolute = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
        player.moveTo(absolute.x, absolute.y, absolute.z, player.getYRot(), player.getXRot());
    }
}
