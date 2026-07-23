package com.boostermod.combat;

import com.boostermod.BoosterMod;
import com.boostermod.item.BoosterEquipment;
import com.boostermod.item.BoosterMotionTicker;
import com.boostermod.upgrade.BoosterUpgradeHelper;
import com.boostermod.upgrade.BoosterUpgradeType;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;

/**
 * 推进破击共享辅助：战斗窗口（推进中 + 结束后宽限）、加长触及、持剑强制横扫、宽容命中等。
 */
public final class BoostStrikeSupport {
    /**
     * 默认触及 3.0；破击窗口内额外 +3.5 → 约 6.5 格。
     * 属性用于准星/原版校验；实际锁定还靠 {@link BoostStrikeTargeting} 辅助选取。
     */
    public static final double REACH_BONUS = 3.5;
    /** 服务端 canInteract 额外松弛（叠在 range + 协议 margin 上）。 */
    public static final double SERVER_REACH_SLACK = 2.0;
    /** 玩家碰撞箱外扩：与生物相交即视为可贴身命中。 */
    public static final double BODY_HIT_MARGIN = 2.25;
    /** 辅助锁定搜索距离下限。 */
    public static final double ASSIST_RANGE_MIN = 6.5;
    /** 辅助锁定允许的最大侧向偏差平方（约 2.5 格）。 */
    public static final double ASSIST_MAX_LATERAL_SQR = 6.25;
    /** 横扫包围盒相对原版 (1, 0.25, 1) 的放大。 */
    public static final double SWEEP_INFLATE_XZ = 3.0;
    public static final double SWEEP_INFLATE_Y = 1.0;
    /** 横扫最大距离平方：原版 9（3 格），推进破击 49（7 格）。 */
    public static final double SWEEP_RANGE_SQR = 49.0;
    public static final double VANILLA_SWEEP_RANGE_SQR = 9.0;
    /**
     * 推进结束后仍视为破击窗口的时长（10 tick = 0.5 秒）。
     * 避免推力刚结束立刻挥砍算不上破击、叠层被「掐断」。
     */
    public static final int POST_BOOST_GRACE_TICKS = 10;

    private static final ResourceLocation REACH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(BoosterMod.MOD_ID, "boost_strike_reach");

    /** 服务端宽限截止 tick（exclusive）：now &lt; until 仍在窗口内。 */
    private static final Map<UUID, Integer> GRACE_UNTIL_TICK = new ConcurrentHashMap<>();

    private BoostStrikeSupport() {}

    public static double assistRange(Player player) {
        return Math.max(player.entityInteractionRange() + 1.0, ASSIST_RANGE_MIN);
    }

    public static boolean hasBoostStrikeUpgrade(LivingEntity entity) {
        return BoosterEquipment.find(entity)
                .map(equipped -> BoosterUpgradeHelper.hasUpgrade(
                        equipped.stack(),
                        BoosterUpgradeType.BOOST_STRIKE,
                        entity.registryAccess()))
                .orElse(false);
    }

    /**
     * 破击战斗窗口：已装升级，且（正在推进 <strong>或</strong> 推进结束后宽限期内）。
     */
    public static boolean isBoostStrikeWindow(Player player) {
        if (!hasBoostStrikeUpgrade(player)) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (BoosterMotionTicker.isBoosting(serverPlayer)) {
                return true;
            }
            return isInPostBoostGrace(serverPlayer);
        }
        return BoostStrikeClientHooks.isClientBoostStrikeWindow(player);
    }

    public static boolean isInPostBoostGrace(ServerPlayer player) {
        Integer until = GRACE_UNTIL_TICK.get(player.getUUID());
        if (until == null) {
            return false;
        }
        return player.server.getTickCount() < until;
    }

    public static void onBoostStart(ServerPlayer player) {
        GRACE_UNTIL_TICK.remove(player.getUUID());
        if (hasBoostStrikeUpgrade(player)) {
            applyReachBonus(player);
        }
    }

    public static void onBoostEnd(ServerPlayer player) {
        if (!hasBoostStrikeUpgrade(player)) {
            removeReachBonus(player);
            GRACE_UNTIL_TICK.remove(player.getUUID());
            return;
        }
        int until = player.server.getTickCount() + POST_BOOST_GRACE_TICKS;
        GRACE_UNTIL_TICK.put(player.getUUID(), until);
        applyReachBonus(player);
    }

    public static void tickGrace(MinecraftServer server) {
        int now = server.getTickCount();
        Iterator<Map.Entry<UUID, Integer>> it = GRACE_UNTIL_TICK.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            if (now < entry.getValue()) {
                continue;
            }
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null && !BoosterMotionTicker.isBoosting(player)) {
                removeReachBonus(player);
            }
        }
    }

    public static void clearGrace(UUID playerId) {
        GRACE_UNTIL_TICK.remove(playerId);
    }

    public static boolean shouldForceSwordSweep(Player player) {
        if (!isBoostStrikeWindow(player)) {
            return false;
        }
        ItemStack main = player.getMainHandItem();
        return main.getItem() instanceof SwordItem;
    }

    /**
     * 破击窗口内强制近战「满蓄力 + 暴击」（跳过下落/离地/非疾跑，且连点不吃半蓄力惩罚）。
     * 覆盖：推进推力全程 + 结束后 {@link #POST_BOOST_GRACE_TICKS} 宽限。
     */
    public static boolean shouldForceCritical(Player player) {
        return isBoostStrikeWindow(player);
    }

    public static void applyReachBonus(Player player) {
        AttributeInstance attr = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (attr == null || attr.getModifier(REACH_MODIFIER_ID) != null) {
            return;
        }
        attr.addTransientModifier(new AttributeModifier(
                REACH_MODIFIER_ID, REACH_BONUS, AttributeModifier.Operation.ADD_VALUE));
    }

    public static void removeReachBonus(Player player) {
        AttributeInstance attr = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (attr != null) {
            attr.removeModifier(REACH_MODIFIER_ID);
        }
    }

    /**
     * 客户端挂钩占位：由 client 源集提供实现；服务端默认 false。
     */
    public static final class BoostStrikeClientHooks {
        private static ClientWindowPredicate predicate = player -> false;

        private BoostStrikeClientHooks() {}

        public static void setPredicate(ClientWindowPredicate predicate) {
            BoostStrikeClientHooks.predicate = predicate != null ? predicate : p -> false;
        }

        public static boolean isClientBoostStrikeWindow(Player player) {
            return predicate.isActive(player);
        }

        @FunctionalInterface
        public interface ClientWindowPredicate {
            boolean isActive(Player player);
        }
    }
}
