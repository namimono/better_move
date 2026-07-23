package com.boostermod.upgrade;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class BoosterUpgradeItem extends Item {
    private final BoosterUpgradeType type;

    public BoosterUpgradeItem(Properties properties, BoosterUpgradeType type) {
        super(properties);
        this.type = type;
    }

    public BoosterUpgradeType getUpgradeType() {
        return type;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Temporary enchanted-book style UI; not an actual enchantment.
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(type.getTooltipKey()).withStyle(ChatFormatting.GRAY));
    }
}
