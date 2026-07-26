package com.boostermod.gametest;

import com.boostermod.BoosterMod;
import com.boostermod.item.BoosterLeggingsItem;
import com.boostermod.upgrade.BoosterUpgradeHelper;
import com.boostermod.upgrade.BoosterUpgradeType;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 武装装备拾取与保全：通过公开实体槽位、物品栏与掉落物验收。
 */
public class ArmedVillagerEquipmentGameTest {
    private static final BlockPos PLATFORM = new BlockPos(3, 1, 3);
    private static final int TIMEOUT = 200;

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void adultVillagerPicksUpBoosterIntoLegs(GameTestHelper helper) {
        Villager villager = spawnAdultVillager(helper, VillagerProfession.NONE);
        spawnGroundItem(helper, new ItemStack(BoosterMod.BOOSTER_LEGGINGS_IRON));

        helper.succeedWhen(() -> {
            ItemStack legs = villager.getItemBySlot(EquipmentSlot.LEGS);
            helper.assertTrue(
                    legs.is(BoosterMod.BOOSTER_LEGGINGS_IRON),
                    "护腿槽应立即装备推进器");
            helper.assertTrue(inventoryDoesNotContainBooster(villager), "推进器不得进入内部物品栏");
            helper.assertTrue(noGroundBooster(helper), "地面推进器应被拾取");
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void adultVillagerPicksUpSwordIntoMainHand(GameTestHelper helper) {
        Villager villager = spawnAdultVillager(helper, VillagerProfession.NONE);
        spawnGroundItem(helper, new ItemStack(Items.IRON_SWORD));

        helper.succeedWhen(() -> {
            ItemStack mainHand = villager.getItemBySlot(EquipmentSlot.MAINHAND);
            helper.assertTrue(mainHand.is(Items.IRON_SWORD), "主手应立即装备剑");
            helper.assertTrue(inventoryDoesNotContain(villager, Items.IRON_SWORD), "剑不得进入内部物品栏");
            helper.assertTrue(noGroundItem(helper, Items.IRON_SWORD), "地面剑应被拾取");
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void occupiedLegsRejectsReplacementBooster(GameTestHelper helper) {
        Villager villager = spawnAdultVillager(helper, VillagerProfession.NONE);
        villager.setItemSlot(EquipmentSlot.LEGS, new ItemStack(BoosterMod.BOOSTER_LEGGINGS_COPPER));
        spawnGroundItem(helper, new ItemStack(BoosterMod.BOOSTER_LEGGINGS_DIAMOND));

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(
                    villager.getItemBySlot(EquipmentSlot.LEGS).is(BoosterMod.BOOSTER_LEGGINGS_COPPER),
                    "已占用护腿槽不得被替换");
            helper.assertTrue(
                    groundItemCount(helper, BoosterMod.BOOSTER_LEGGINGS_DIAMOND) >= 1,
                    "拒绝拾取时地面推进器应保留");
            helper.assertTrue(inventoryDoesNotContainBooster(villager), "拒绝时不得吞进物品栏");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void occupiedMainHandRejectsReplacementSword(GameTestHelper helper) {
        Villager villager = spawnAdultVillager(helper, VillagerProfession.NONE);
        villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
        spawnGroundItem(helper, new ItemStack(Items.DIAMOND_SWORD));

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(
                    villager.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.WOODEN_SWORD),
                    "已占用主手不得被替换");
            helper.assertTrue(
                    groundItemCount(helper, Items.DIAMOND_SWORD) >= 1,
                    "拒绝拾取时地面剑应保留");
            helper.assertTrue(inventoryDoesNotContain(villager, Items.DIAMOND_SWORD), "拒绝时不得吞进物品栏");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void farmerCanEquipBooster(GameTestHelper helper) {
        Villager farmer = spawnAdultVillager(helper, VillagerProfession.FARMER);
        spawnGroundItem(helper, new ItemStack(BoosterMod.BOOSTER_LEGGINGS_GOLD));

        helper.succeedWhen(() -> helper.assertTrue(
                farmer.getItemBySlot(EquipmentSlot.LEGS).is(BoosterMod.BOOSTER_LEGGINGS_GOLD),
                "职业村民应能装备推进器"));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void nitwitCanEquipSword(GameTestHelper helper) {
        Villager nitwit = spawnAdultVillager(helper, VillagerProfession.NITWIT);
        spawnGroundItem(helper, new ItemStack(Items.STONE_SWORD));

        helper.succeedWhen(() -> helper.assertTrue(
                nitwit.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.STONE_SWORD),
                "傻子村民应能装备剑"));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void babyVillagerDoesNotEquipArmedGear(GameTestHelper helper) {
        preparePlatform(helper);
        Villager baby = helper.spawn(EntityType.VILLAGER, PLATFORM.above());
        baby.setBaby(true);
        spawnGroundItem(helper, new ItemStack(BoosterMod.BOOSTER_LEGGINGS_IRON));
        spawnGroundItem(helper, new ItemStack(Items.IRON_SWORD));

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(baby.getItemBySlot(EquipmentSlot.LEGS).isEmpty(), "幼年村民不得装备推进器");
            helper.assertTrue(baby.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "幼年村民不得装备剑");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void wanderingTraderDoesNotEquipArmedGear(GameTestHelper helper) {
        preparePlatform(helper);
        WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, PLATFORM.above());
        spawnGroundItem(helper, new ItemStack(BoosterMod.BOOSTER_LEGGINGS_IRON));
        spawnGroundItem(helper, new ItemStack(Items.IRON_SWORD));

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(trader.getItemBySlot(EquipmentSlot.LEGS).isEmpty(), "流浪商人不得装备推进器");
            helper.assertTrue(trader.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "流浪商人不得装备剑");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "armedGearMobGriefing", timeoutTicks = TIMEOUT)
    public void mobGriefingFalseBlocksNewPickupButKeepsExisting(GameTestHelper helper) {
        Villager villager = spawnAdultVillager(helper, VillagerProfession.NONE);
        villager.setItemSlot(EquipmentSlot.LEGS, new ItemStack(BoosterMod.BOOSTER_LEGGINGS_COPPER));
        ServerLevel level = helper.getLevel();
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer());
        spawnGroundItem(helper, new ItemStack(Items.IRON_SWORD));

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(
                    villager.getItemBySlot(EquipmentSlot.LEGS).is(BoosterMod.BOOSTER_LEGGINGS_COPPER),
                    "关闭 mobGriefing 不得卸下已有装备");
            helper.assertTrue(
                    villager.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(),
                    "关闭 mobGriefing 禁止新拾取剑");
            helper.assertTrue(groundItemCount(helper, Items.IRON_SWORD) >= 1, "地面剑应保留");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void foodPickupStillWorks(GameTestHelper helper) {
        Villager villager = spawnAdultVillager(helper, VillagerProfession.NONE);
        spawnGroundItem(helper, new ItemStack(Items.BREAD));

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    villager.getInventory().countItem(Items.BREAD) > 0,
                    "食物拾取行为应保持进入内部物品栏");
            helper.assertTrue(noGroundItem(helper, Items.BREAD), "地面面包应被拾取");
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void deathDropsArmedGearIntact(GameTestHelper helper) {
        Villager villager = spawnAdultVillager(helper, VillagerProfession.NONE);

        ItemStack booster = new ItemStack(BoosterMod.BOOSTER_LEGGINGS_DIAMOND);
        booster.setDamageValue(17);
        SimpleContainer upgrades = new SimpleContainer(BoosterUpgradeHelper.MAX_SLOTS);
        upgrades.setItem(0, new ItemStack(BoosterMod.AIR_DASH_UPGRADE));
        BoosterUpgradeHelper.saveContainer(
                booster, upgrades, 1, helper.getLevel().registryAccess());

        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setDamageValue(9);
        var sharpness = helper.getLevel()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS);
        sword.enchant(sharpness, 3);

        spawnGroundItem(helper, booster);
        spawnGroundItem(helper, sword);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(
                            villager.getItemBySlot(EquipmentSlot.LEGS).is(BoosterMod.BOOSTER_LEGGINGS_DIAMOND),
                            "死亡前应已通过拾取装备推进器");
                    helper.assertTrue(
                            villager.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.DIAMOND_SWORD),
                            "死亡前应已通过拾取装备剑");
                })
                .thenExecute(() -> villager.hurt(
                        helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE))
                .thenWaitUntil(() -> {
                    ItemStack droppedBooster = findDroppedStack(helper, BoosterMod.BOOSTER_LEGGINGS_DIAMOND);
                    ItemStack droppedSword = findDroppedStack(helper, Items.DIAMOND_SWORD);
                    helper.assertTrue(!droppedBooster.isEmpty(), "死亡应掉落推进器");
                    helper.assertTrue(!droppedSword.isEmpty(), "死亡应掉落剑");
                    helper.assertValueEqual(17, droppedBooster.getDamageValue(), "推进器耐久应保留");
                    helper.assertValueEqual(9, droppedSword.getDamageValue(), "剑耐久应保留");
                    helper.assertTrue(
                            BoosterUpgradeHelper.hasUpgrade(
                                    droppedBooster,
                                    BoosterUpgradeType.AIR_DASH,
                                    helper.getLevel().registryAccess()),
                            "推进器升级项应保留");
                    helper.assertTrue(
                            droppedSword.getEnchantments().getLevel(sharpness) == 3,
                            "剑附魔应保留");
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void zombieVillagerDoesNotEquipBooster(GameTestHelper helper) {
        preparePlatform(helper);
        var zombieVillager = helper.spawn(EntityType.ZOMBIE_VILLAGER, PLATFORM.above());
        spawnGroundItem(helper, new ItemStack(BoosterMod.BOOSTER_LEGGINGS_IRON));

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(
                    !(zombieVillager.getItemBySlot(EquipmentSlot.LEGS).getItem()
                            instanceof BoosterLeggingsItem),
                    "僵尸村民不得装备本模组推进器");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void farmerSeedPickupStillWorks(GameTestHelper helper) {
        Villager farmer = spawnAdultVillager(helper, VillagerProfession.FARMER);
        spawnGroundItem(helper, new ItemStack(Items.WHEAT_SEEDS));

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    farmer.getInventory().countItem(Items.WHEAT_SEEDS) > 0,
                    "职业种子拾取应保持进入内部物品栏");
            helper.assertTrue(noGroundItem(helper, Items.WHEAT_SEEDS), "地面种子应被拾取");
        });
    }

    private static Villager spawnAdultVillager(GameTestHelper helper, VillagerProfession profession) {
        preparePlatform(helper);
        Villager villager = helper.spawn(EntityType.VILLAGER, PLATFORM.above());
        villager.setVillagerData(villager.getVillagerData().setProfession(profession));
        villager.setBaby(false);
        return villager;
    }

    private static void preparePlatform(GameTestHelper helper) {
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }

    private static void spawnGroundItem(GameTestHelper helper, ItemStack stack) {
        spawnGroundItemAt(helper, stack, new Vec3(PLATFORM.getX() + 0.5, PLATFORM.getY() + 1.1, PLATFORM.getZ() + 0.5));
    }

    private static void spawnGroundItemAt(GameTestHelper helper, ItemStack stack, Vec3 relative) {
        Vec3 absolute = helper.absoluteVec(relative);
        ItemEntity itemEntity = new ItemEntity(
                helper.getLevel(), absolute.x, absolute.y, absolute.z, stack);
        itemEntity.setNoPickUpDelay();
        itemEntity.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(itemEntity);
    }

    private static boolean inventoryDoesNotContainBooster(Villager villager) {
        SimpleContainer inventory = villager.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).getItem() instanceof BoosterLeggingsItem) {
                return false;
            }
        }
        return true;
    }

    private static boolean inventoryDoesNotContain(Villager villager, Item item) {
        return villager.getInventory().countItem(item) == 0;
    }

    private static boolean noGroundBooster(GameTestHelper helper) {
        return groundItemCount(helper, BoosterMod.BOOSTER_LEGGINGS_IRON) == 0;
    }

    private static boolean noGroundItem(GameTestHelper helper, Item item) {
        return groundItemCount(helper, item) == 0;
    }

    private static int groundItemCount(GameTestHelper helper, Item item) {
        AABB box = helper.getBounds().inflate(2.0);
        List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, box);
        int count = 0;
        for (ItemEntity itemEntity : items) {
            if (itemEntity.getItem().is(item)) {
                count += itemEntity.getItem().getCount();
            }
        }
        return count;
    }

    private static ItemStack findDroppedStack(GameTestHelper helper, Item item) {
        AABB box = helper.getBounds().inflate(2.0);
        for (ItemEntity itemEntity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
            if (itemEntity.getItem().is(item)) {
                return itemEntity.getItem();
            }
        }
        return ItemStack.EMPTY;
    }
}
