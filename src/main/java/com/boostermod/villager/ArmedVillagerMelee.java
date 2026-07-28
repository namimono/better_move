package com.boostermod.villager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * 村民剑击：近战可达时攻击，间隔 20 tick，命中才损耗剑耐久。
 */
public final class ArmedVillagerMelee {
    public static final int ATTACK_INTERVAL_TICKS = 20;

    private static final Map<UUID, Long> LAST_ATTACK_GAME_TIME = new ConcurrentHashMap<>();

    private ArmedVillagerMelee() {}

    public static void clear(Villager villager) {
        LAST_ATTACK_GAME_TIME.remove(villager.getUUID());
    }

    public static void clearAll() {
        LAST_ATTACK_GAME_TIME.clear();
    }

    /**
     * 在交战 tick 中尝试村民剑击。
     *
     * @return true 表示本 tick 成功命中
     */
    public static boolean tickAttack(Villager villager, LivingEntity target) {
        long gameTime = villager.level().getGameTime();
        Long last = LAST_ATTACK_GAME_TIME.get(villager.getUUID());
        if (last != null && gameTime - last < ATTACK_INTERVAL_TICKS) {
            return false;
        }

        if (!villager.isWithinMeleeAttackRange(target) || !villager.hasLineOfSight(target)) {
            return false;
        }

        ItemStack sword = villager.getMainHandItem();
        if (!ArmedVillagerEquipment.isSword(sword)) {
            return false;
        }

        villager.swing(InteractionHand.MAIN_HAND);
        // 先记录间隔，避免同 tick 重入；未命中也占用间隔，避免每 tick 狂挥。
        LAST_ATTACK_GAME_TIME.put(villager.getUUID(), gameTime);
        boolean hit = villager.doHurtTarget(target);
        if (hit) {
            sword.hurtAndBreak(1, villager, EquipmentSlot.MAINHAND);
        }
        return hit;
    }
}
