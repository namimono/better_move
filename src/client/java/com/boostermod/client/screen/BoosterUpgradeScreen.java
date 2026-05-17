package com.boostermod.client.screen;

import com.boostermod.screen.BoosterUpgradeMenu;
import com.boostermod.upgrade.BoosterUpgradeHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class BoosterUpgradeScreen extends AbstractContainerScreen<BoosterUpgradeMenu> {
    private static final int VANILLA_BACKGROUND = 0xFFC6C6C6;
    private static final int VANILLA_HIGHLIGHT = 0xFFFFFFFF;
    private static final int VANILLA_LOWLIGHT = 0xFF555555;
    private static final int VANILLA_SHADOW = 0xFF8B8B8B;
    private static final int VANILLA_SLOT = 0xFF373737;
    private static final int LOCKED_SLOT_OVERLAY = 0x7F000000;
    private static final int LOCKED_MARK_COLOR = 0xFF6F6F6F;
    private static final int TEXT_COLOR = 0xFF404040;

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
        drawVanillaPanel(graphics, x, y, imageWidth, imageHeight);
        drawVanillaInset(graphics, x + 26, y + 25, 124, 36);
        drawPlayerInventorySlots(graphics, x, y);

        for (int i = 0; i < BoosterUpgradeHelper.MAX_SLOTS; i++) {
            boolean active = i < menu.getActiveSlots();
            int slotX = x + 35 + i * 18;
            int slotY = y + 35;
            drawVanillaSlot(graphics, slotX - 1, slotY - 1);
            if (!active) {
                graphics.fill(slotX, slotY, slotX + 16, slotY + 16, LOCKED_SLOT_OVERLAY);
                graphics.drawCenteredString(font, "x", slotX + 8, slotY + 4, LOCKED_MARK_COLOR);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT_COLOR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_COLOR, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        super.render(graphics, mouseX, mouseY, delta);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private static void drawVanillaPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, VANILLA_BACKGROUND);
        graphics.fill(x, y, x + width, y + 1, VANILLA_HIGHLIGHT);
        graphics.fill(x, y, x + 1, y + height, VANILLA_HIGHLIGHT);
        graphics.fill(x + width - 1, y + 1, x + width, y + height, VANILLA_LOWLIGHT);
        graphics.fill(x + 1, y + height - 1, x + width, y + height, VANILLA_LOWLIGHT);
    }

    private static void drawVanillaInset(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, VANILLA_SHADOW);
        graphics.fill(x, y, x + width, y + 1, VANILLA_LOWLIGHT);
        graphics.fill(x, y, x + 1, y + height, VANILLA_LOWLIGHT);
        graphics.fill(x + width - 1, y + 1, x + width, y + height, VANILLA_HIGHLIGHT);
        graphics.fill(x + 1, y + height - 1, x + width, y + height, VANILLA_HIGHLIGHT);
    }

    private static void drawVanillaSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, VANILLA_BACKGROUND);
        graphics.fill(x, y, x + 18, y + 1, VANILLA_LOWLIGHT);
        graphics.fill(x, y, x + 1, y + 18, VANILLA_LOWLIGHT);
        graphics.fill(x + 17, y + 1, x + 18, y + 18, VANILLA_HIGHLIGHT);
        graphics.fill(x + 1, y + 17, x + 18, y + 18, VANILLA_HIGHLIGHT);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, VANILLA_SLOT);
    }

    private static void drawPlayerInventorySlots(GuiGraphics graphics, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawVanillaSlot(graphics, x + 7 + col * 18, y + 83 + row * 18);
            }
        }

        for (int col = 0; col < 9; col++) {
            drawVanillaSlot(graphics, x + 7 + col * 18, y + 141);
        }
    }
}
