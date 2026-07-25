package com.boostermod.appearance;

import com.boostermod.tier.BoosterTier;
import net.minecraft.resources.ResourceLocation;

/**
 * 等级 → 穿戴外观 / 物品栏图标的资源定位。
 * 只依赖等级，与已装升级项、推进/蓄力/过载状态无关。
 */
public final class BoosterAppearanceTextures {
    private static final String NAMESPACE = "boostermod";

    private BoosterAppearanceTextures() {}

    /** 护甲层渲染用的穿戴外观贴图（完整 textures/... 路径）。 */
    public static ResourceLocation wornTexture(BoosterTier tier) {
        return ResourceLocation.fromNamespaceAndPath(
                NAMESPACE, "textures/models/armor/booster_leggings_" + tier.getId() + "_layer_2.png");
    }

    /** 物品 model 的 layer0 贴图 id（不含 textures/ 前缀与 .png）。 */
    public static ResourceLocation inventoryIcon(BoosterTier tier) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, "item/booster_leggings_" + tier.getId());
    }
}
