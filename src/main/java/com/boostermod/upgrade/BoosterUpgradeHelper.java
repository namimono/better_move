package com.boostermod.upgrade;

import com.boostermod.item.BoosterLeggingsItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class BoosterUpgradeHelper {
    public static final int MAX_SLOTS = 6;
    private static final String UPGRADES_KEY = "BoostermodUpgrades";
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;

    private BoosterUpgradeHelper() {}

    public static SimpleContainer loadContainer(ItemStack boosterStack, HolderLookup.Provider registries) {
        SimpleContainer container = new SimpleContainer(MAX_SLOTS);
        if (!(boosterStack.getItem() instanceof BoosterLeggingsItem)) {
            return container;
        }

        CustomData data = boosterStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = data.copyTag();
        if (root.contains(UPGRADES_KEY, TAG_LIST)) {
            container.fromTag(root.getList(UPGRADES_KEY, TAG_COMPOUND), registries);
        }
        return container;
    }

    public static void saveContainer(ItemStack boosterStack, SimpleContainer container, int activeSlots, HolderLookup.Provider registries) {
        if (!(boosterStack.getItem() instanceof BoosterLeggingsItem)) {
            return;
        }

        SimpleContainer activeContainer = new SimpleContainer(MAX_SLOTS);
        for (int i = 0; i < Math.min(activeSlots, MAX_SLOTS); i++) {
            ItemStack stack = container.getItem(i);
            if (isUpgrade(stack)) {
                activeContainer.setItem(i, stack.copy());
            }
        }

        CustomData.update(DataComponents.CUSTOM_DATA, boosterStack, tag -> {
            ListTag upgrades = activeContainer.createTag(registries);
            if (upgrades.isEmpty()) {
                tag.remove(UPGRADES_KEY);
            } else {
                tag.put(UPGRADES_KEY, upgrades);
            }
        });
    }

    public static boolean isUpgrade(ItemStack stack) {
        return stack.getItem() instanceof BoosterUpgradeItem;
    }

    public static boolean hasUpgrade(ItemStack boosterStack, BoosterUpgradeType type, HolderLookup.Provider registries) {
        for (ItemStack stack : loadUpgrades(boosterStack, registries)) {
            if (stack.getItem() instanceof BoosterUpgradeItem upgradeItem
                    && upgradeItem.getUpgradeType() == type) {
                return true;
            }
        }
        return false;
    }

    public static List<ItemStack> loadUpgrades(ItemStack boosterStack, HolderLookup.Provider registries) {
        SimpleContainer container = loadContainer(boosterStack, registries);
        List<ItemStack> upgrades = new ArrayList<>();
        int activeSlots = boosterStack.getItem() instanceof BoosterLeggingsItem boosterItem
                ? boosterItem.getTier().getUpgradeSlots()
                : 0;
        for (int i = 0; i < Math.min(activeSlots, MAX_SLOTS); i++) {
            ItemStack stack = container.getItem(i);
            if (isUpgrade(stack)) {
                upgrades.add(stack);
            }
        }
        return upgrades;
    }
}
