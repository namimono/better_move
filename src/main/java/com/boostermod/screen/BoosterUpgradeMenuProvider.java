package com.boostermod.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class BoosterUpgradeMenuProvider implements ExtendedScreenHandlerFactory<BoosterUpgradeOpenData> {
    private final InteractionHand hand;

    public BoosterUpgradeMenuProvider(InteractionHand hand) {
        this.hand = hand;
    }

    @Override
    public BoosterUpgradeOpenData getScreenOpeningData(ServerPlayer player) {
        return new BoosterUpgradeOpenData(hand == InteractionHand.MAIN_HAND);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.boostermod.booster_upgrade");
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
        return new BoosterUpgradeMenu(syncId, inventory, hand);
    }
}
