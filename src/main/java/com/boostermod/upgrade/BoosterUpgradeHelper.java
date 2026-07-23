package com.boostermod.upgrade;

import com.boostermod.BoosterMod;
import com.boostermod.item.BoosterLeggingsItem;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class BoosterUpgradeHelper {
    public static final int MAX_SLOTS = 6;
    /** Full item stacks for the upgrade UI (slot-indexed). */
    private static final String UPGRADES_KEY = "BoostermodUpgrades";
    /** Authoritative gameplay flags — survives fragile nested ItemStack parse issues. */
    private static final String UPGRADE_TYPES_KEY = "BoostermodUpgradeTypes";
    private static final int TAG_LIST = Tag.TAG_LIST;
    private static final int TAG_COMPOUND = Tag.TAG_COMPOUND;
    private static final int TAG_STRING = Tag.TAG_STRING;

    private BoosterUpgradeHelper() {}

    public static SimpleContainer loadContainer(ItemStack boosterStack, HolderLookup.Provider registries) {
        SimpleContainer container = new SimpleContainer(MAX_SLOTS);
        if (!(boosterStack.getItem() instanceof BoosterLeggingsItem)) {
            return container;
        }

        CompoundTag root = boosterStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        boolean loadedItems = loadItemsInto(container, root, registries);
        if (!loadedItems) {
            // Rebuild visible stacks from authoritative type ids when nested items failed to parse.
            int slot = 0;
            for (BoosterUpgradeType type : readStoredTypes(root)) {
                if (slot >= MAX_SLOTS) {
                    break;
                }
                ItemStack upgrade = createUpgradeStack(type);
                if (!upgrade.isEmpty()) {
                    container.setItem(slot++, upgrade);
                }
            }
        }
        return container;
    }

    public static void saveContainer(
            ItemStack boosterStack,
            SimpleContainer container,
            int activeSlots,
            HolderLookup.Provider registries) {
        if (!(boosterStack.getItem() instanceof BoosterLeggingsItem)) {
            return;
        }

        ListTag items = new ListTag();
        ListTag types = new ListTag();
        Set<BoosterUpgradeType> seen = EnumSet.noneOf(BoosterUpgradeType.class);
        int limit = Math.min(activeSlots, MAX_SLOTS);
        for (int i = 0; i < limit; i++) {
            ItemStack stack = container.getItem(i);
            if (!(stack.getItem() instanceof BoosterUpgradeItem upgradeItem)) {
                continue;
            }
            BoosterUpgradeType type = upgradeItem.getUpgradeType();
            if (!seen.add(type)) {
                continue;
            }

            CompoundTag itemTag = new CompoundTag();
            itemTag.putByte("Slot", (byte) i);
            items.add(stack.save(registries, itemTag));
            types.add(StringTag.valueOf(type.name()));
        }

        CustomData.update(DataComponents.CUSTOM_DATA, boosterStack, tag -> {
            if (items.isEmpty()) {
                tag.remove(UPGRADES_KEY);
                tag.remove(UPGRADE_TYPES_KEY);
            } else {
                tag.put(UPGRADES_KEY, items);
                tag.put(UPGRADE_TYPES_KEY, types);
            }
        });
    }

    public static boolean isUpgrade(ItemStack stack) {
        return stack.getItem() instanceof BoosterUpgradeItem;
    }

    public static boolean hasUpgrade(ItemStack boosterStack, BoosterUpgradeType type, HolderLookup.Provider registries) {
        if (!(boosterStack.getItem() instanceof BoosterLeggingsItem)) {
            return false;
        }
        CompoundTag root = boosterStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (root.contains(UPGRADE_TYPES_KEY, TAG_LIST)) {
            return readStoredTypes(root).contains(type);
        }
        // Legacy fallback: only item list was stored.
        for (ItemStack stack : loadUpgrades(boosterStack, registries)) {
            if (stack.getItem() instanceof BoosterUpgradeItem upgradeItem
                    && upgradeItem.getUpgradeType() == type) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsUpgradeType(SimpleContainer container, BoosterUpgradeType type, int activeSlots) {
        int limit = Math.min(activeSlots, Math.min(container.getContainerSize(), MAX_SLOTS));
        for (int i = 0; i < limit; i++) {
            ItemStack stack = container.getItem(i);
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

    public static List<BoosterUpgradeType> listUpgradeTypes(ItemStack boosterStack, HolderLookup.Provider registries) {
        if (!(boosterStack.getItem() instanceof BoosterLeggingsItem)) {
            return List.of();
        }
        CompoundTag root = boosterStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (root.contains(UPGRADE_TYPES_KEY, TAG_LIST)) {
            return List.copyOf(readStoredTypes(root));
        }
        List<BoosterUpgradeType> types = new ArrayList<>();
        for (ItemStack stack : loadUpgrades(boosterStack, registries)) {
            if (stack.getItem() instanceof BoosterUpgradeItem upgradeItem) {
                types.add(upgradeItem.getUpgradeType());
            }
        }
        return types;
    }

    public static ItemStack createUpgradeStack(BoosterUpgradeType type) {
        ResourceLocation id = BoosterMod.id(type.getItemId());
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    private static boolean loadItemsInto(
            SimpleContainer container, CompoundTag root, HolderLookup.Provider registries) {
        if (!root.contains(UPGRADES_KEY, TAG_LIST)) {
            return false;
        }
        ListTag items = root.getList(UPGRADES_KEY, TAG_COMPOUND);
        if (items.isEmpty()) {
            return false;
        }

        boolean any = false;
        boolean hasSlotIndex = items.getCompound(0).contains("Slot", Tag.TAG_BYTE);
        if (hasSlotIndex) {
            for (int i = 0; i < items.size(); i++) {
                CompoundTag itemTag = items.getCompound(i);
                int slot = itemTag.getByte("Slot") & 0xFF;
                if (slot >= MAX_SLOTS) {
                    continue;
                }
                ItemStack stack = ItemStack.parse(registries, itemTag).orElse(ItemStack.EMPTY);
                if (isUpgrade(stack)) {
                    container.setItem(slot, stack);
                    any = true;
                }
            }
            return any;
        }

        // Legacy SimpleContainer.createTag format (no Slot bytes).
        int slot = 0;
        for (int i = 0; i < items.size() && slot < MAX_SLOTS; i++) {
            ItemStack stack = ItemStack.parse(registries, items.getCompound(i)).orElse(ItemStack.EMPTY);
            if (isUpgrade(stack)) {
                container.setItem(slot++, stack);
                any = true;
            }
        }
        return any;
    }

    private static Set<BoosterUpgradeType> readStoredTypes(CompoundTag root) {
        Set<BoosterUpgradeType> types = EnumSet.noneOf(BoosterUpgradeType.class);
        if (!root.contains(UPGRADE_TYPES_KEY, TAG_LIST)) {
            return types;
        }
        ListTag list = root.getList(UPGRADE_TYPES_KEY, TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            String raw = list.getString(i);
            try {
                types.add(BoosterUpgradeType.valueOf(raw.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Skip unknown/legacy ids.
            }
        }
        return types;
    }
}
