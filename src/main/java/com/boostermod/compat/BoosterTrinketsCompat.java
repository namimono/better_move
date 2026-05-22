package com.boostermod.compat;

import com.boostermod.BoosterMod;
import com.boostermod.item.BoosterEquipment;
import com.boostermod.item.BoosterLeggingsItem;
import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.Trinket;
import dev.emi.trinkets.api.TrinketItem;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class BoosterTrinketsCompat {
    public static final String SLOT_GROUP = "legs";
    public static final String SLOT_NAME = "booster";

    private BoosterTrinketsCompat() {}

    public static void init() {
        register(BoosterMod.BOOSTER_LEGGINGS_COPPER);
        register(BoosterMod.BOOSTER_LEGGINGS_IRON);
        register(BoosterMod.BOOSTER_LEGGINGS_GOLD);
        register(BoosterMod.BOOSTER_LEGGINGS_DIAMOND);
        register(BoosterMod.BOOSTER_LEGGINGS_NETHERITE);

        BoosterEquipment.registerTrinketSupport(BoosterTrinketsCompat::findInTrinketSlot, TrinketItem::equipItem);
    }

    private static void register(Item item) {
        TrinketsApi.registerTrinket(item, new BoosterTrinketBehavior());
    }

    private static Optional<BoosterEquipment.Equipped> findInTrinketSlot(LivingEntity entity) {
        return TrinketsApi.getTrinketComponent(entity).flatMap(component -> {
            BoosterEquipment.Equipped[] found = new BoosterEquipment.Equipped[1];
            component.forEach((slot, stack) -> {
                if (found[0] == null && stack.getItem() instanceof BoosterLeggingsItem item) {
                    found[0] = new BoosterEquipment.Equipped(
                            item,
                            stack,
                            (player, level, boosterStack) -> boosterStack.hurtAndBreak(
                                    1, level, player, ignored -> TrinketsApi.onTrinketBroken(boosterStack, slot, player)));
                }
            });
            return Optional.ofNullable(found[0]);
        });
    }

    private static final class BoosterTrinketBehavior implements Trinket {
        @Override
        public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
            return SLOT_GROUP.equals(slot.inventory().getSlotType().getGroup())
                    && SLOT_NAME.equals(slot.inventory().getSlotType().getName());
        }

        @Override
        public boolean canEquipFromUse(ItemStack stack, LivingEntity entity) {
            return true;
        }

        @Override
        public Holder<SoundEvent> getEquipSound(ItemStack stack, SlotReference slot, LivingEntity entity) {
            return SoundEvents.ARMOR_EQUIP_LEATHER;
        }
    }
}
