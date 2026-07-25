package com.boostermod.appearance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.boostermod.tier.BoosterTier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import org.junit.jupiter.api.Test;

/**
 * 主接缝：等级 → 穿戴外观 / 物品栏图标定位。
 * 外观只跟等级走，与升级项无关。
 */
class BoosterAppearanceTexturesTest {

    private static final List<BoosterTier> APPEARANCE_TIERS = List.of(
            BoosterTier.COPPER,
            BoosterTier.IRON,
            BoosterTier.GOLD,
            BoosterTier.DIAMOND,
            BoosterTier.NETHERITE);

    @Test
    void wornTextures_matchExpectedPaths_forFiveActiveTiers() {
        assertEquals(
                ResourceLocation.fromNamespaceAndPath(
                        "boostermod", "textures/models/armor/booster_leggings_copper_layer_2.png"),
                BoosterAppearanceTextures.wornTexture(BoosterTier.COPPER));
        assertEquals(
                ResourceLocation.fromNamespaceAndPath(
                        "boostermod", "textures/models/armor/booster_leggings_iron_layer_2.png"),
                BoosterAppearanceTextures.wornTexture(BoosterTier.IRON));
        assertEquals(
                ResourceLocation.fromNamespaceAndPath(
                        "boostermod", "textures/models/armor/booster_leggings_gold_layer_2.png"),
                BoosterAppearanceTextures.wornTexture(BoosterTier.GOLD));
        assertEquals(
                ResourceLocation.fromNamespaceAndPath(
                        "boostermod", "textures/models/armor/booster_leggings_diamond_layer_2.png"),
                BoosterAppearanceTextures.wornTexture(BoosterTier.DIAMOND));
        assertEquals(
                ResourceLocation.fromNamespaceAndPath(
                        "boostermod", "textures/models/armor/booster_leggings_netherite_layer_2.png"),
                BoosterAppearanceTextures.wornTexture(BoosterTier.NETHERITE));
    }

    @Test
    void inventoryIcons_matchExpectedPaths_forFiveActiveTiers() {
        assertEquals(
                ResourceLocation.fromNamespaceAndPath("boostermod", "item/booster_leggings_copper"),
                BoosterAppearanceTextures.inventoryIcon(BoosterTier.COPPER));
        assertEquals(
                ResourceLocation.fromNamespaceAndPath("boostermod", "item/booster_leggings_iron"),
                BoosterAppearanceTextures.inventoryIcon(BoosterTier.IRON));
        assertEquals(
                ResourceLocation.fromNamespaceAndPath("boostermod", "item/booster_leggings_gold"),
                BoosterAppearanceTextures.inventoryIcon(BoosterTier.GOLD));
        assertEquals(
                ResourceLocation.fromNamespaceAndPath("boostermod", "item/booster_leggings_diamond"),
                BoosterAppearanceTextures.inventoryIcon(BoosterTier.DIAMOND));
        assertEquals(
                ResourceLocation.fromNamespaceAndPath("boostermod", "item/booster_leggings_netherite"),
                BoosterAppearanceTextures.inventoryIcon(BoosterTier.NETHERITE));
    }

    @Test
    void fiveActiveTiers_haveDistinctWornAndInventoryPaths() {
        Set<ResourceLocation> worn = new HashSet<>();
        Set<ResourceLocation> icons = new HashSet<>();
        for (BoosterTier tier : APPEARANCE_TIERS) {
            assertTrue(worn.add(BoosterAppearanceTextures.wornTexture(tier)), () -> "duplicate worn for " + tier);
            assertTrue(icons.add(BoosterAppearanceTextures.inventoryIcon(tier)), () -> "duplicate icon for " + tier);
            assertNotEquals(
                    BoosterAppearanceTextures.wornTexture(tier),
                    BoosterAppearanceTextures.inventoryIcon(tier));
        }
        assertEquals(5, worn.size());
        assertEquals(5, icons.size());
    }

    @Test
    void boosterLeggingsItem_isNotArmorItem_soNoArmorDefenseFromAppearance() {
        // ADR-0001：接穿戴外观但不走 ArmorItem，避免附带护甲值。
        assertTrue(
                !ArmorItem.class.isAssignableFrom(com.boostermod.item.BoosterLeggingsItem.class),
                "BoosterLeggingsItem must remain non-ArmorItem");
    }
}
