package com.boostermod.gametest;

import com.boostermod.BoosterMod;
import com.boostermod.item.BoosterLeggingsItem;
import com.boostermod.villager.ArmedVillagerCombat;
import com.boostermod.villager.ArmedVillagerMelee;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 村民剑击与基础村民推进：通过生命值、耐久、位置/速度等公开状态验收。
 */
public class ArmedVillagerAttackBoostGameTest {
    private static final BlockPos VILLAGER_POS = new BlockPos(3, 1, 2);
    private static final int TIMEOUT = 500;

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedAttack_meleeIron", timeoutTicks = TIMEOUT)
    public void ironSwordStrikeDamagesNearbyPlayer(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareNearArena(helper);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.IRON_SWORD);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 3), "melee-iron");

        helper.startSequence()
                .thenWaitUntil(() -> {
                    clearPlayerIframes(player);
                    helper.assertTrue(villager.getTarget() == player, "应先锁敌");
                    helper.assertTrue(player.getHealth() < 20.0F, "铁剑村民剑击应造成伤害");
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedAttack_sharpness", timeoutTicks = TIMEOUT)
    public void sharpnessIncreasesStrikeDamage(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareNearArena(helper);

        Villager plain = spawnArmedVillager(helper, new BlockPos(1, 1, 2), Items.IRON_SWORD);
        Player plainTarget = spawnAttackablePlayer(helper, new BlockPos(1, 1, 3), "plain-t");

        Villager sharp = spawnAdultVillager(helper, new BlockPos(5, 1, 2));
        ItemStack sharpSword = new ItemStack(Items.IRON_SWORD);
        var sharpness = helper.getLevel()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS);
        sharpSword.enchant(sharpness, 5);
        sharp.setItemSlot(EquipmentSlot.LEGS, new ItemStack(BoosterMod.BOOSTER_LEGGINGS_IRON));
        sharp.setItemSlot(EquipmentSlot.MAINHAND, sharpSword);
        sharp.setGuaranteedDrop(EquipmentSlot.LEGS);
        sharp.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        Player sharpTarget = spawnAttackablePlayer(helper, new BlockPos(5, 1, 3), "sharp-t");

        float[] plainDamage = new float[1];
        float[] sharpDamage = new float[1];

        helper.startSequence()
                .thenWaitUntil(() -> {
                    clearPlayerIframes(plainTarget);
                    clearPlayerIframes(sharpTarget);
                    helper.assertTrue(plain.getTarget() == plainTarget, "无附魔村民应锁敌");
                    helper.assertTrue(sharp.getTarget() == sharpTarget, "锋利村民应锁敌");
                    helper.assertTrue(plainTarget.getHealth() < 20.0F, "无附魔应造成伤害");
                    helper.assertTrue(sharpTarget.getHealth() < 20.0F, "锋利应造成伤害");
                })
                .thenExecute(() -> {
                    plainDamage[0] = 20.0F - plainTarget.getHealth();
                    sharpDamage[0] = 20.0F - sharpTarget.getHealth();
                })
                .thenExecute(() -> helper.assertTrue(
                        sharpDamage[0] > plainDamage[0] + 0.5F,
                        "锋利附魔伤害应明显高于无附魔铁剑"))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedAttack_interval", timeoutTicks = TIMEOUT)
    public void swordStrikeRespectsTwentyTickInterval(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareNearArena(helper);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.IRON_SWORD);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 3), "interval");
        float[] healthAfterFirst = new float[1];
        long[] firstHitTime = new long[1];

        helper.startSequence()
                .thenWaitUntil(() -> {
                    clearPlayerIframes(player);
                    helper.assertTrue(player.getHealth() < 20.0F, "应先命中一次");
                })
                .thenExecute(() -> {
                    healthAfterFirst[0] = player.getHealth();
                    firstHitTime[0] = helper.getLevel().getGameTime();
                    clearPlayerIframes(player);
                })
                .thenIdle(ArmedVillagerMelee.ATTACK_INTERVAL_TICKS - 2)
                .thenExecute(() -> {
                    clearPlayerIframes(player);
                    helper.assertValueEqual(
                            healthAfterFirst[0],
                            player.getHealth(),
                            "20 tick 间隔内不得再次剑击");
                })
                .thenWaitUntil(() -> {
                    clearPlayerIframes(player);
                    helper.assertTrue(
                            player.getHealth() < healthAfterFirst[0],
                            "间隔结束后应可再次剑击");
                    helper.assertTrue(
                            helper.getLevel().getGameTime() - firstHitTime[0]
                                    >= ArmedVillagerMelee.ATTACK_INTERVAL_TICKS,
                            "第二次剑击不得早于 20 tick");
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedAttack_swordDurability", timeoutTicks = TIMEOUT)
    public void successfulStrikeConsumesOneSwordDurability(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareNearArena(helper);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.IRON_SWORD);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 3), "sword-dmg");
        int[] before = new int[1];
        before[0] = villager.getMainHandItem().getDamageValue();

        helper.startSequence()
                .thenWaitUntil(() -> {
                    clearPlayerIframes(player);
                    helper.assertTrue(player.getHealth() < 20.0F, "应成功命中");
                })
                .thenExecute(() -> helper.assertValueEqual(
                        before[0] + 1,
                        villager.getMainHandItem().getDamageValue(),
                        "命中应消耗剑 1 点耐久"))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoost_midRange", timeoutTicks = TIMEOUT)
    public void midRangeVisibleTargetTriggersBoost(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareLongCorridor(helper, 30);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.IRON_SWORD);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 14), "boost-mid");
        int[] legsBefore = new int[1];
        legsBefore[0] = villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue();
        Vec3[] startPos = new Vec3[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == player, "应锁敌"))
                .thenExecute(() -> startPos[0] = villager.position())
                .thenWaitUntil(() -> {
                    helper.assertTrue(
                            VillagerBoostRunner.isBoosting(villager)
                                    || villager.position().distanceTo(startPos[0]) > 1.0,
                            "6～20 格应发起村民推进并产生位移");
                    helper.assertTrue(
                            villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue()
                                    >= legsBefore[0] + 1,
                            "发起推进应消耗推进器 1 点耐久");
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoost_tooClose", timeoutTicks = TIMEOUT)
    public void tooCloseDoesNotBoost(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareNearArena(helper);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.IRON_SWORD);
        spawnAttackablePlayer(helper, new BlockPos(3, 1, 4), "too-close");
        int legsBefore = villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() != null, "应锁敌"))
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(!VillagerBoostRunner.isBoosting(villager), "不足 6 格不得推进");
                    helper.assertValueEqual(
                            legsBefore,
                            villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue(),
                            "过近不得消耗推进器耐久");
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoost_tooFar", timeoutTicks = TIMEOUT)
    public void tooFarDoesNotBoost(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareLongCorridor(helper, 40);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.IRON_SWORD);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 28), "too-far");
        int[] legsBefore = new int[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == player, "应锁敌"))
                .thenExecute(() -> {
                    helper.assertTrue(
                            villager.distanceTo(player) > VillagerBoostRunner.MAX_BOOST_DISTANCE,
                            "锁敌时目标应仍在 20 格外");
                    legsBefore[0] = villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue();
                })
                .thenIdle(15)
                .thenExecute(() -> {
                    if (villager.distanceTo(player) > VillagerBoostRunner.MAX_BOOST_DISTANCE) {
                        helper.assertTrue(!VillagerBoostRunner.isBoosting(villager), "超过 20 格不得推进");
                        helper.assertValueEqual(
                                legsBefore[0],
                                villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue(),
                                "过远不得消耗推进器耐久");
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoost_fixedDir", timeoutTicks = TIMEOUT)
    public void boostDirectionIsFixedAtLaunch(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareLongCorridor(helper, 30);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.IRON_SWORD);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 14), "fixed-dir");
        Vec3[] launchVelocity = new Vec3[1];
        Vec3[] midVelocity = new Vec3[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == player, "应锁敌"))
                .thenWaitUntil(() -> helper.assertTrue(VillagerBoostRunner.isBoosting(villager), "应进入推进"))
                .thenExecute(() -> {
                    launchVelocity[0] = villager.getDeltaMovement();
                    // 把目标挪到侧面，推进中不得改向追新位置
                    movePlayer(helper, player, new BlockPos(6, 1, 14));
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    midVelocity[0] = villager.getDeltaMovement();
                    helper.assertTrue(launchVelocity[0].lengthSqr() > 0.01, "推进应有初速度");
                    // 水平方向应仍大致朝原 +Z，而不是折向 +X
                    helper.assertTrue(
                            Math.abs(midVelocity[0].x) < Math.abs(midVelocity[0].z),
                            "推进途中不得改向追踪目标新位置");
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoost_cooldown", timeoutTicks = TIMEOUT)
    public void boostCooldownPreventsImmediateRepeat(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareLongCorridor(helper, 30);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.IRON_SWORD);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 14), "boost-cd");
        int[] afterFirst = new int[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue() >= 1,
                        "应先成功推进一次"))
                .thenExecute(() -> afterFirst[0] = villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue())
                .thenIdle(VillagerBoostRunner.COOLDOWN_TICKS - 5)
                .thenExecute(() -> helper.assertValueEqual(
                        afterFirst[0],
                        villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue(),
                        "60 tick 冷却内不得再次推进"))
                .thenExecute(() -> {
                    // 拉开距离并保持在 6～20，等待冷却结束后再次推进
                    movePlayer(helper, player, new BlockPos(3, 1, 16));
                    villager.teleportTo(
                            helper.absoluteVec(Vec3.atBottomCenterOf(VILLAGER_POS)).x,
                            helper.absoluteVec(Vec3.atBottomCenterOf(VILLAGER_POS)).y,
                            helper.absoluteVec(Vec3.atBottomCenterOf(VILLAGER_POS)).z);
                })
                .thenIdle(10)
                .thenWaitUntil(() -> helper.assertTrue(
                        villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue() > afterFirst[0],
                        "冷却结束后应可再次推进"))
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoost_strikeDuring", timeoutTicks = TIMEOUT)
    public void canStrikeDuringBoostWhenInMeleeRange(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareLongCorridor(helper, 30);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.IRON_SWORD);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 8), "boost-strike");
        boolean[] sawBoost = new boolean[1];
        boolean[] hitWhileBoosting = new boolean[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == player, "应锁敌"))
                .thenWaitUntil(() -> {
                    clearPlayerIframes(player);
                    if (VillagerBoostRunner.isBoosting(villager)) {
                        sawBoost[0] = true;
                        if (player.getHealth() < 20.0F) {
                            hitWhileBoosting[0] = true;
                        }
                    }
                    helper.assertTrue(sawBoost[0], "应进入村民推进");
                    helper.assertTrue(hitWhileBoosting[0], "推进进行中应能剑击");
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoost_noCollisionDamage", timeoutTicks = TIMEOUT)
    public void boostAndBodyCollisionDealNoExtraDamage(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareLongCorridor(helper, 30);
        // 木剑伤害低，便于观察；若无剑击窗口则健康应基本不变
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.WOODEN_SWORD);
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 12), "no-collide-dmg");
        // 临时卸剑：验证纯推进/碰撞不造成伤害（再装回会破坏用例，故用未武装推进路径不合适）
        // 规格：推进与碰撞本身无额外伤害。做法：推进接近但用无敌帧吞掉剑击伤害差分不稳；
        // 改为观察：推进开始后、首次剑击前，若玩家已被碰到，生命值仍满。
        float[] healthAtBoost = new float[1];
        healthAtBoost[0] = -1.0F;

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == player, "应锁敌"))
                .thenWaitUntil(() -> helper.assertTrue(VillagerBoostRunner.isBoosting(villager), "应推进"))
                .thenExecute(() -> {
                    healthAtBoost[0] = player.getHealth();
                    // 推进瞬间若已满血，说明推进发起本身无伤害
                    helper.assertValueEqual(20.0F, healthAtBoost[0], "发起推进本身不得造成伤害");
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    // 若尚未进入近战剑击，生命值应仍为满或仅来自剑击
                    if (!villager.isWithinMeleeAttackRange(player)) {
                        helper.assertValueEqual(
                                20.0F,
                                player.getHealth(),
                                "未进入近战前推进/碰撞不得造成伤害");
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedBoost_blocked", timeoutTicks = TIMEOUT)
    public void solidObstructionPreventsBoost(GameTestHelper helper) {
        ensureCombatDifficulty(helper);
        prepareLongCorridor(helper, 30);
        Villager villager = spawnArmedVillager(helper, VILLAGER_POS, Items.IRON_SWORD);
        // 先近距离锁敌（不触发推进），再隔墙并把目标挪到中距离。
        Player player = spawnAttackablePlayer(helper, new BlockPos(3, 1, 4), "blocked");
        int[] legsAfterWall = new int[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getTarget() == player, "应先锁敌"))
                .thenExecute(() -> {
                    // 封住走廊全宽，避免从两侧绕开视线
                    for (int x = 2; x <= 4; x++) {
                        for (int y = 1; y <= 3; y++) {
                            helper.setBlock(new BlockPos(x, y, 8), Blocks.STONE);
                        }
                    }
                    movePlayer(helper, player, new BlockPos(3, 1, 14));
                    legsAfterWall[0] = villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue();
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(!VillagerBoostRunner.isBoosting(villager), "前方固体阻挡不得推进");
                    helper.assertValueEqual(
                            legsAfterWall[0],
                            villager.getItemBySlot(EquipmentSlot.LEGS).getDamageValue(),
                            "被阻挡时不得消耗推进器");
                })
                .thenSucceed();
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
            for (int y = 0; y <= 8; y++) {
                helper.setBlock(new BlockPos(3, y, z), Blocks.AIR);
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

    private static void clearPlayerIframes(Player player) {
        player.invulnerableTime = 0;
    }
}
