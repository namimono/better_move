package com.boostermod.gametest;

import com.boostermod.BoosterMod;
import com.boostermod.upgrade.BoosterUpgradeHelper;
import com.boostermod.upgrade.BoosterUpgradeType;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * 破壁升级项入口：合成、安装、持久化、同类型去重与移除。
 */
public class WallBreakUpgradeGameTest {
    private static final int TIMEOUT = 40;

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void shapedRecipeCraftsWallBreakUpgrade(GameTestHelper helper) {
        List<ItemStack> grid = List.of(
                ItemStack.EMPTY, new ItemStack(Items.GUNPOWDER), ItemStack.EMPTY,
                new ItemStack(Items.GUNPOWDER), new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.GUNPOWDER),
                ItemStack.EMPTY, new ItemStack(Items.REDSTONE), ItemStack.EMPTY);
        CraftingInput input = CraftingInput.of(3, 3, grid);

        Optional<RecipeHolder<CraftingRecipe>> match = helper.getLevel()
                .getServer()
                .getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());

        helper.assertTrue(match.isPresent(), "应匹配破壁升级项有序合成配方");
        ItemStack result = match.get().value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(BoosterMod.WALL_BREAK_UPGRADE), "合成结果应为破壁升级项");
        helper.assertValueEqual(1, result.getCount(), "合成数量应为 1");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void installSaveLoadAndQueryWallBreakUpgrade(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        ItemStack booster = new ItemStack(BoosterMod.BOOSTER_LEGGINGS_IRON);
        SimpleContainer upgrades = new SimpleContainer(BoosterUpgradeHelper.MAX_SLOTS);
        upgrades.setItem(0, new ItemStack(BoosterMod.WALL_BREAK_UPGRADE));

        BoosterUpgradeHelper.saveContainer(booster, upgrades, 2, registries);

        helper.assertTrue(
                BoosterUpgradeHelper.hasUpgrade(booster, BoosterUpgradeType.WALL_BREAK, registries),
                "安装后应能查询到破壁升级项");
        helper.assertTrue(
                BoosterUpgradeHelper.listUpgradeTypes(booster, registries).contains(BoosterUpgradeType.WALL_BREAK),
                "升级类型列表应包含 WALL_BREAK");

        SimpleContainer reloaded = BoosterUpgradeHelper.loadContainer(booster, registries);
        helper.assertTrue(
                reloaded.getItem(0).is(BoosterMod.WALL_BREAK_UPGRADE),
                "重新打开升级容器后仍应显示破壁升级项");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void duplicateWallBreakUpgradeIsRejectedOnSave(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        ItemStack booster = new ItemStack(BoosterMod.BOOSTER_LEGGINGS_DIAMOND);
        SimpleContainer upgrades = new SimpleContainer(BoosterUpgradeHelper.MAX_SLOTS);
        upgrades.setItem(0, new ItemStack(BoosterMod.WALL_BREAK_UPGRADE));
        upgrades.setItem(1, new ItemStack(BoosterMod.WALL_BREAK_UPGRADE));

        helper.assertTrue(
                BoosterUpgradeHelper.containsUpgradeType(upgrades, BoosterUpgradeType.WALL_BREAK, 3),
                "容器内应已检测到破壁升级项类型");

        BoosterUpgradeHelper.saveContainer(booster, upgrades, 3, registries);
        helper.assertTrue(
                BoosterUpgradeHelper.hasUpgrade(booster, BoosterUpgradeType.WALL_BREAK, registries),
                "去重后仍应保留破壁升级项");
        helper.assertValueEqual(
                1,
                BoosterUpgradeHelper.listUpgradeTypes(booster, registries).size(),
                "同类型不得重复安装");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void removingWallBreakUpgradeClearsCapabilityFlag(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        ItemStack booster = new ItemStack(BoosterMod.BOOSTER_LEGGINGS_GOLD);
        SimpleContainer upgrades = new SimpleContainer(BoosterUpgradeHelper.MAX_SLOTS);
        upgrades.setItem(0, new ItemStack(BoosterMod.WALL_BREAK_UPGRADE));
        BoosterUpgradeHelper.saveContainer(booster, upgrades, 2, registries);
        helper.assertTrue(
                BoosterUpgradeHelper.hasUpgrade(booster, BoosterUpgradeType.WALL_BREAK, registries),
                "预置安装应成功");

        upgrades.setItem(0, ItemStack.EMPTY);
        BoosterUpgradeHelper.saveContainer(booster, upgrades, 2, registries);

        helper.assertTrue(
                !BoosterUpgradeHelper.hasUpgrade(booster, BoosterUpgradeType.WALL_BREAK, registries),
                "移除后能力标记应同步消失");
        helper.assertTrue(
                BoosterUpgradeHelper.listUpgradeTypes(booster, registries).isEmpty(),
                "移除后升级类型列表应为空");
        helper.succeed();
    }
}
