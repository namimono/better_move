package com.boostermod.client.compat;

import com.boostermod.BoosterMod;
import dev.emi.trinkets.api.client.TrinketRendererRegistry;
import net.minecraft.world.item.Item;

/**
 * Trinkets 客户端接线：仅在检测到 Trinkets 时经反射加载，避免未安装时拉入其类。
 */
public final class BoosterTrinketsClientCompat {
    private BoosterTrinketsClientCompat() {}

    public static void register() {
        BoosterTrinketRenderer renderer = new BoosterTrinketRenderer();
        register(BoosterMod.BOOSTER_LEGGINGS_COPPER, renderer);
        register(BoosterMod.BOOSTER_LEGGINGS_IRON, renderer);
        register(BoosterMod.BOOSTER_LEGGINGS_GOLD, renderer);
        register(BoosterMod.BOOSTER_LEGGINGS_DIAMOND, renderer);
        register(BoosterMod.BOOSTER_LEGGINGS_NETHERITE, renderer);
    }

    private static void register(Item item, BoosterTrinketRenderer renderer) {
        TrinketRendererRegistry.registerRenderer(item, renderer);
    }
}
