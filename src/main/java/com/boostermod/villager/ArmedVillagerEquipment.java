package com.boostermod.villager;

import com.boostermod.item.BoosterLeggingsItem;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * 武装装备拾取规则：推进器进护腿槽、剑进主手，不进入村民内部物品栏。
 */
public final class ArmedVillagerEquipment {
    private ArmedVillagerEquipment() {}

    public static boolean isAdultVillager(Villager villager) {
        return !villager.isBaby();
    }

    public static boolean isBooster(ItemStack stack) {
        return stack.getItem() instanceof BoosterLeggingsItem;
    }

    public static boolean isSword(ItemStack stack) {
        return stack.is(ItemTags.SWORDS);
    }

    /** 武装村民：成年 + 护腿推进器 + 主手剑；实时派生，不持久化。 */
    public static boolean isArmed(Villager villager) {
        return isAdultVillager(villager)
                && isBooster(villager.getItemBySlot(EquipmentSlot.LEGS))
                && isSword(villager.getItemBySlot(EquipmentSlot.MAINHAND));
    }

    public static boolean canEquipBooster(Villager villager) {
        return isAdultVillager(villager) && villager.getItemBySlot(EquipmentSlot.LEGS).isEmpty();
    }

    public static boolean canEquipSword(Villager villager) {
        return isAdultVillager(villager) && villager.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();
    }

    public static boolean wantsArmedGear(Villager villager, ItemStack stack) {
        if (isBooster(stack)) {
            return canEquipBooster(villager);
        }
        if (isSword(stack)) {
            return canEquipSword(villager);
        }
        return false;
    }

    /**
     * @return true 表示已处理（装备或明确拒绝进物品栏），调用方应取消原版拾取。
     */
    public static boolean tryEquipFromGround(Villager villager, ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem();
        if (isBooster(stack)) {
            if (!canEquipBooster(villager)) {
                return true;
            }
            equipOne(villager, EquipmentSlot.LEGS, itemEntity);
            return true;
        }
        if (isSword(stack)) {
            if (!canEquipSword(villager)) {
                return true;
            }
            equipOne(villager, EquipmentSlot.MAINHAND, itemEntity);
            return true;
        }
        return false;
    }

    private static void equipOne(Villager villager, EquipmentSlot slot, ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem();
        ItemStack equipped = stack.copyWithCount(1);
        villager.setItemSlot(slot, equipped);
        villager.setGuaranteedDrop(slot);
        villager.onItemPickup(itemEntity);
        villager.take(itemEntity, 1);
        stack.shrink(1);
        if (stack.isEmpty()) {
            itemEntity.discard();
        }
    }
}
