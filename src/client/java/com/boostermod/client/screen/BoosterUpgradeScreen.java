package com.boostermod.client.screen;

import com.boostermod.screen.BoosterUpgradeMenu;
import com.boostermod.upgrade.BoosterUpgradeHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class BoosterUpgradeScreen extends AbstractContainerScreen<BoosterUpgradeMenu> {
    private static final int BG_COLOR = 0xFF20242C;
    private static final int PANEL_COLOR = 0xFF2D3440;
    private static final int SLOT_COLOR = 0xFF11141A;
    private static final int LOCKED_SLOT_COLOR = 0xFF3A2B2F;
    private static final int BORDER_COLOR = 0xFF6E7888;
    private static final int LOCKED_BORDER_COLOR = 0xFF8A4F57;

    public BoosterUpgradeScreen(BoosterUpgradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = 72;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, BG_COLOR);
        graphics.fill(x + 6, y + 18, x + imageWidth - 6, y + 62, PANEL_COLOR);
        graphics.renderOutline(x, y, imageWidth, imageHeight, BORDER_COLOR);

        for (int i = 0; i < BoosterUpgradeHelper.MAX_SLOTS; i++) {
            boolean active = i < menu.getActiveSlots();
            int slotX = x + 35 + i * 18;
            int slotY = y + 35;
            graphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17,
                    active ? BORDER_COLOR : LOCKED_BORDER_COLOR);
            graphics.fill(slotX, slotY, slotX + 16, slotY + 16,
                    active ? SLOT_COLOR : LOCKED_SLOT_COLOR);
            if (!active) {
                graphics.drawCenteredString(font, "x", slotX + 8, slotY + 4, 0xFFC9A0A0);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFE7EDF5, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFE7EDF5, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        super.render(graphics, mouseX, mouseY, delta);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
