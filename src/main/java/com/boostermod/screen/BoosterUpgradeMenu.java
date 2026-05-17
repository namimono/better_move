package com.boostermod.screen;

import com.boostermod.BoosterMod;
import com.boostermod.item.BoosterLeggingsItem;
import com.boostermod.upgrade.BoosterUpgradeHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class BoosterUpgradeMenu extends AbstractContainerMenu {
    private static final int UPGRADE_SLOT_COUNT = BoosterUpgradeHelper.MAX_SLOTS;
    private static final int PLAYER_INVENTORY_START = UPGRADE_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final Inventory playerInventory;
    private final InteractionHand hand;
    private final ItemStack boosterStack;
    private final SimpleContainer upgrades;
    private final int activeSlots;

    public BoosterUpgradeMenu(int syncId, Inventory inventory, BoosterUpgradeOpenData data) {
        this(syncId, inventory, data.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
    }

    public BoosterUpgradeMenu(int syncId, Inventory inventory, InteractionHand hand) {
        super(BoosterMod.BOOSTER_UPGRADE_MENU, syncId);
        this.playerInventory = inventory;
        this.hand = hand;
        this.boosterStack = inventory.player.getItemInHand(hand);
        this.activeSlots = boosterStack.getItem() instanceof BoosterLeggingsItem boosterItem
                ? boosterItem.getTier().getUpgradeSlots()
                : 0;
        this.upgrades = BoosterUpgradeHelper.loadContainer(boosterStack, inventory.player.registryAccess());

        addUpgradeSlots();
        addPlayerInventorySlots(inventory);
    }

    public int getActiveSlots() {
        return activeSlots;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand) == boosterStack
                && boosterStack.getItem() instanceof BoosterLeggingsItem;
    }

    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        super.slotsChanged(container);
        save(playerInventory.player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        save(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();
        if (index < UPGRADE_SLOT_COUNT) {
            if (!moveItemStackTo(original, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (BoosterUpgradeHelper.isUpgrade(original)) {
            if (!moveItemStackTo(original, 0, activeSlots, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < PLAYER_INVENTORY_END) {
            if (!moveItemStackTo(original, HOTBAR_START, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(original, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false)) {
            return ItemStack.EMPTY;
        }

        if (original.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return copy;
    }

    private void addUpgradeSlots() {
        int startX = 35;
        int y = 35;
        for (int i = 0; i < UPGRADE_SLOT_COUNT; i++) {
            addSlot(new UpgradeSlot(upgrades, i, startX + i * 18, y, i));
        }
    }

    private void addPlayerInventorySlots(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            Slot slot = new Slot(inventory, col, 8 + col * 18, 142);
            if (hand == InteractionHand.MAIN_HAND && col == inventory.selected) {
                slot = new LockedSlot(inventory, col, 8 + col * 18, 142);
            }
            addSlot(slot);
        }
    }

    private void save(Player player) {
        if (!player.level().isClientSide) {
            BoosterUpgradeHelper.saveContainer(boosterStack, upgrades, activeSlots, player.registryAccess());
        }
    }

    private final class UpgradeSlot extends Slot {
        private final int slotIndex;

        private UpgradeSlot(SimpleContainer container, int index, int x, int y, int slotIndex) {
            super(container, index, x, y);
            this.slotIndex = slotIndex;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isActive() && BoosterUpgradeHelper.isUpgrade(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return isActive();
        }

        @Override
        public boolean isActive() {
            return slotIndex < activeSlots;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }

    private static final class LockedSlot extends Slot {
        private LockedSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
