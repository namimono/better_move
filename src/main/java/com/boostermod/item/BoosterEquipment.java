package com.boostermod.item;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 统一查询推进器装备位置：未安装 Trinkets 时仅护腿槽；安装后优先饰品槽，其次护腿槽。
 */
public final class BoosterEquipment {
    private static Function<LivingEntity, Optional<Equipped>> trinketFinder = entity -> Optional.empty();
    private static BiFunction<Player, ItemStack, Boolean> trinketEquip = (player, stack) -> false;
    private static boolean trinketsEnabled;

    private BoosterEquipment() {}

    public static boolean isTrinketsEnabled() {
        return trinketsEnabled;
    }

    public static void registerTrinketSupport(
            Function<LivingEntity, Optional<Equipped>> finder,
            BiFunction<Player, ItemStack, Boolean> equip) {
        trinketFinder = finder;
        trinketEquip = equip;
        trinketsEnabled = true;
    }

    public static void initTrinketsCompat() {
        if (!FabricLoader.getInstance().isModLoaded("trinkets")) {
            return;
        }
        try {
            Class.forName("com.boostermod.compat.BoosterTrinketsCompat")
                    .getDeclaredMethod("init")
                    .invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Trinkets is present but Booster Trinkets compat failed to load", e);
        }
    }

    public static Optional<Equipped> find(LivingEntity entity) {
        Optional<Equipped> fromTrinket = trinketFinder.apply(entity);
        if (fromTrinket.isPresent()) {
            return fromTrinket;
        }
        return findInLegsSlot(entity);
    }

    public static boolean tryEquipToTrinketSlot(Player player, ItemStack stack) {
        return trinketsEnabled && trinketEquip.apply(player, stack);
    }

    private static Optional<Equipped> findInLegsSlot(LivingEntity entity) {
        ItemStack legs = entity.getItemBySlot(EquipmentSlot.LEGS);
        if (!(legs.getItem() instanceof BoosterLeggingsItem item)) {
            return Optional.empty();
        }
        return Optional.of(new Equipped(
                item,
                legs,
                (player, level, boosterStack) -> boosterStack.hurtAndBreak(1, player, EquipmentSlot.LEGS)));
    }

    @FunctionalInterface
    public interface BoostDamageSink {
        void damage(ServerPlayer player, ServerLevel level, ItemStack stack);
    }

    public record Equipped(BoosterLeggingsItem item, ItemStack stack, BoostDamageSink damageSink) {
        public void applyBoostDamage(ServerPlayer player, ServerLevel level) {
            damageSink.damage(player, level, stack);
        }
    }
}
