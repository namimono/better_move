package com.boostermod.combat;

import com.boostermod.BoosterMod;
import com.boostermod.charge.OverloadExplosion;
import com.boostermod.item.BoosterEquipment;
import com.boostermod.item.BoosterLeggingsItem;
import com.boostermod.item.BoosterMotionTicker;
import com.boostermod.network.BoosterStrikeFeedbackPayload;
import com.boostermod.network.BoosterStrikeStackPayload;
import com.boostermod.tier.BoosterTier;
import com.boostermod.upgrade.BoosterUpgradeHelper;
import com.boostermod.upgrade.BoosterUpgradeType;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 推进破击：仅当玩家在 ActiveBoost 中、已装升级，且对<strong>主攻击目标</strong>造成有效近战伤害时结算。
 * 命中/击杀攻击力加成为叠层（可叠加至品质上限），到期后清零；推进本身不造成伤害。
 */
public final class BoostStrikeHandler {
    private static final ResourceLocation ATTACK_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(BoosterMod.MOD_ID, "boost_strike_attack");

    /** 本 tick 主攻击目标（AttackEntityCallback 记录，用于排除扫击次要目标）。 */
    private static final Map<UUID, PrimaryAttack> PRIMARY_ATTACKS = new ConcurrentHashMap<>();
    /** 本段结算防重：同一玩家对同一目标同一 tick 只奖励一次。 */
    private static final Map<UUID, SettledKey> SETTLED = new ConcurrentHashMap<>();
    /** 当前叠层攻击加成总量（与属性修饰同步）。 */
    private static final Map<UUID, Double> ATTACK_STACK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> ATTACK_BUFF_DEADLINE = new ConcurrentHashMap<>();
    /**
     * 调试锁定：叠层保持为指定值，不受命中叠层/到期清零影响，直到 unlock。
     * value ≥ 0；锁定中 deadline 不生效。
     */
    private static final Map<UUID, Double> STACK_LOCK = new ConcurrentHashMap<>();
    /** 锁定时 HUD 显示的「剩余」tick（仅展示用，服务端不按此到期）。 */
    private static final int LOCKED_HUD_REMAINING_TICKS = 20 * 60 * 60;
    private static final ThreadLocal<Boolean> APPLYING_BONUS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private BoostStrikeHandler() {}

    public static void init() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (!(entity instanceof LivingEntity living) || living.isDeadOrDying()) {
                return InteractionResult.PASS;
            }
            if (!BoostStrikeSupport.isBoostStrikeWindow(serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (OverloadExplosion.explodedThisTick(serverPlayer)
                    || BoosterMotionTicker.shouldSuppressStrikeForOverloadImpact(serverPlayer)) {
                return InteractionResult.PASS;
            }
            BoosterEquipment.Equipped equipped = BoosterEquipment.find(serverPlayer).orElse(null);
            if (equipped == null) {
                return InteractionResult.PASS;
            }
            if (!BoosterUpgradeHelper.hasUpgrade(
                    equipped.stack(), BoosterUpgradeType.BOOST_STRIKE, serverPlayer.registryAccess())) {
                return InteractionResult.PASS;
            }
            int tick = serverPlayer.server.getTickCount();
            PRIMARY_ATTACKS.put(
                    serverPlayer.getUUID(),
                    new PrimaryAttack(living.getUUID(), tick, equipped.item().getTier(), equipped.item()));
            return InteractionResult.PASS;
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register(BoostStrikeHandler::onAfterDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register(BoostStrikeHandler::onAfterDeath);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                server.execute(() -> clearPlayer(handler.player.getUUID(), handler.player)));
    }

    public static void tickServer(MinecraftServer server) {
        BoostStrikeSupport.tickGrace(server);
        int tick = server.getTickCount();
        PRIMARY_ATTACKS.entrySet().removeIf(entry -> tick - entry.getValue().tick() > 1);
        SETTLED.entrySet().removeIf(entry -> tick - entry.getValue().tick() > 1);

        Iterator<Map.Entry<UUID, Long>> buffIt = ATTACK_BUFF_DEADLINE.entrySet().iterator();
        while (buffIt.hasNext()) {
            Map.Entry<UUID, Long> entry = buffIt.next();
            if (STACK_LOCK.containsKey(entry.getKey())) {
                // 锁定中：不到期；周期性刷新属性与 HUD
                if (tick % 20 == 0) {
                    ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                    if (player != null) {
                        reassertLockedStack(player);
                    }
                }
                continue;
            }
            if (tick < entry.getValue()) {
                // 每 20 tick 心跳，防 HUD 丢包
                if (tick % 20 == 0) {
                    ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                    if (player != null) {
                        syncStack(player);
                    }
                }
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                // clearAttackStack 会从 ATTACK_BUFF_DEADLINE 移除当前项并 sync
                clearAttackStack(player);
            } else {
                ATTACK_STACK.remove(entry.getKey());
                buffIt.remove();
            }
        }

        // 仅有锁定、尚未写入 deadline 的兜底心跳
        if (tick % 20 == 0) {
            for (UUID id : STACK_LOCK.keySet()) {
                if (ATTACK_BUFF_DEADLINE.containsKey(id)) {
                    continue;
                }
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    reassertLockedStack(player);
                }
            }
        }
    }

    /** 玩家加入或需要纠正时下发当前叠层快照。 */
    public static void syncStackToPlayer(ServerPlayer player) {
        if (player != null) {
            syncStack(player);
        }
    }

    public static boolean isStackLocked(ServerPlayer player) {
        return player != null && STACK_LOCK.containsKey(player.getUUID());
    }

    public static double getStackAmount(ServerPlayer player) {
        if (player == null) {
            return 0.0;
        }
        Double locked = STACK_LOCK.get(player.getUUID());
        if (locked != null) {
            return locked;
        }
        return ATTACK_STACK.getOrDefault(player.getUUID(), 0.0);
    }

    /**
     * 将叠层锁定为 {@code amount}（≥0），不因命中/到期变化，直到 {@link #unlockStack}。
     */
    public static void lockStack(ServerPlayer player, double amount) {
        if (player == null) {
            return;
        }
        double clamped = Math.max(0.0, amount);
        STACK_LOCK.put(player.getUUID(), clamped);
        applyAbsoluteStack(player, clamped, /*locked*/ true);
    }

    /**
     * 取消锁定。当前叠层保留并按命中时长起一个普通到期；若为 0 则清零。
     */
    public static void unlockStack(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Double locked = STACK_LOCK.remove(player.getUUID());
        double current = locked != null
                ? locked
                : ATTACK_STACK.getOrDefault(player.getUUID(), 0.0);
        if (current <= 0.0) {
            clearAttackStack(player);
            return;
        }
        int duration = BoostStrikeProfile.forTier(resolveTier(player)).hitDurationTicks();
        applyAbsoluteStack(player, current, /*locked*/ false);
        ATTACK_BUFF_DEADLINE.put(player.getUUID(), player.server.getTickCount() + (long) duration);
        syncStack(player);
    }

    private static void onAfterDamage(
            LivingEntity entity,
            DamageSource source,
            float baseDamageTaken,
            float damageTaken,
            boolean blocked) {
        if (APPLYING_BONUS.get()) {
            return;
        }
        if (blocked || damageTaken <= 0.0f) {
            return;
        }
        ServerPlayer player = meleeAttacker(source);
        if (player == null) {
            return;
        }
        if (OverloadExplosion.explodedThisTick(player)
                || BoosterMotionTicker.shouldSuppressStrikeForOverloadImpact(player)) {
            return;
        }
        PrimaryAttack primary = matchPrimary(player, entity);
        if (primary == null) {
            return;
        }
        if (!markSettled(player, entity)) {
            return;
        }

        BoostStrikeProfile profile = BoostStrikeProfile.forTier(primary.tier());
        if (!entity.isDeadOrDying() && profile.bonusDamage() > 0.0f) {
            APPLYING_BONUS.set(Boolean.TRUE);
            try {
                // 主伤害刚结算会拉起无敌帧，同帧追加破击伤害必须清掉，否则 bonus 常为 0。
                entity.invulnerableTime = 0;
                entity.hurt(player.damageSources().playerAttack(player), profile.bonusDamage());
            } finally {
                APPLYING_BONUS.set(Boolean.FALSE);
            }
        }

        boolean killed = entity.isDeadOrDying();
        // 命中叠命中增量；击杀再叠击杀增量（击杀 = 命中 + 击杀两层）。
        stackAttackBuff(
                player,
                killed ? profile.stackDeltaOnKill() : profile.stackDeltaOnHit(),
                killed ? profile.killDurationTicks() : profile.hitDurationTicks(),
                profile.maxStackBonus());
        if (killed) {
            resetBoosterCooldown(player, primary.boosterItem());
        }
        ServerPlayNetworking.send(player, new BoosterStrikeFeedbackPayload(killed));
    }

    private static void onAfterDeath(LivingEntity entity, DamageSource source) {
        if (APPLYING_BONUS.get()) {
            // Bonus damage landed the killing blow; AFTER_DAMAGE already settled rewards.
            return;
        }
        ServerPlayer player = meleeAttacker(source);
        if (player == null) {
            return;
        }
        PrimaryAttack primary = matchPrimary(player, entity);
        if (primary == null) {
            return;
        }
        if (!markSettled(player, entity)) {
            return;
        }

        BoostStrikeProfile profile = BoostStrikeProfile.forTier(primary.tier());
        // 主伤害直接致死：未走 AFTER_DAMAGE 时补叠「命中 + 击杀」。
        stackAttackBuff(
                player,
                profile.stackDeltaOnKill(),
                profile.killDurationTicks(),
                profile.maxStackBonus());
        resetBoosterCooldown(player, primary.boosterItem());
        ServerPlayNetworking.send(player, new BoosterStrikeFeedbackPayload(true));
    }

    private static ServerPlayer meleeAttacker(DamageSource source) {
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (!(attacker instanceof ServerPlayer player)) {
            return null;
        }
        // Melee: attacker and direct source are the player. Projectiles use a non-player direct entity.
        if (direct != null && direct != player) {
            return null;
        }
        if (!BoostStrikeSupport.isBoostStrikeWindow(player)) {
            return null;
        }
        ItemStack boosterStack = BoosterEquipment.find(player).map(BoosterEquipment.Equipped::stack).orElse(ItemStack.EMPTY);
        if (boosterStack.isEmpty()
                || !BoosterUpgradeHelper.hasUpgrade(
                        boosterStack, BoosterUpgradeType.BOOST_STRIKE, player.registryAccess())) {
            return null;
        }
        return player;
    }

    private static PrimaryAttack matchPrimary(ServerPlayer player, LivingEntity target) {
        PrimaryAttack primary = PRIMARY_ATTACKS.get(player.getUUID());
        if (primary == null) {
            return null;
        }
        int tick = player.server.getTickCount();
        if (primary.tick() != tick && primary.tick() != tick - 1) {
            return null;
        }
        if (!primary.targetId().equals(target.getUUID())) {
            return null;
        }
        return primary;
    }

    private static boolean markSettled(ServerPlayer player, LivingEntity target) {
        int tick = player.server.getTickCount();
        SettledKey key = new SettledKey(target.getUUID(), tick);
        SettledKey previous = SETTLED.putIfAbsent(player.getUUID(), key);
        if (previous == null) {
            return true;
        }
        // Same target same-ish tick already settled.
        if (previous.targetId().equals(target.getUUID())
                && (previous.tick() == tick || previous.tick() == tick - 1)) {
            return false;
        }
        SETTLED.put(player.getUUID(), key);
        return true;
    }

    /**
     * 将 {@code delta} 叠到当前攻击加成上，封顶 {@code maxStack}，并刷新持续时间。
     * 到期后整段叠层清零（不是逐层掉）。
     */
    private static void stackAttackBuff(
            ServerPlayer player, double delta, int durationTicks, double maxStack) {
        if (delta <= 0.0 || durationTicks <= 0) {
            return;
        }
        // 锁定中：保持锁定值，不因命中叠层变化
        Double locked = STACK_LOCK.get(player.getUUID());
        if (locked != null) {
            reassertLockedStack(player);
            return;
        }
        AttributeInstance attr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attr == null) {
            return;
        }

        double current = ATTACK_STACK.getOrDefault(player.getUUID(), 0.0);
        AttributeModifier existing = attr.getModifier(ATTACK_MODIFIER_ID);
        if (existing != null && current <= 0.0) {
            // 地图丢档时以属性修饰为准恢复
            current = existing.amount();
        }

        double next = Math.min(maxStack, current + delta);
        applyAbsoluteStack(player, next, /*locked*/ false);
        if (next > 0.0) {
            long deadline = player.server.getTickCount() + (long) durationTicks;
            ATTACK_BUFF_DEADLINE.put(player.getUUID(), deadline);
        }
        syncStack(player);
    }

    private static void resetBoosterCooldown(ServerPlayer player, BoosterLeggingsItem boosterItem) {
        player.getCooldowns().removeCooldown(boosterItem);
    }

    private static void clearAttackStack(Player player) {
        if (STACK_LOCK.containsKey(player.getUUID())) {
            if (player instanceof ServerPlayer serverPlayer) {
                reassertLockedStack(serverPlayer);
            }
            return;
        }
        ATTACK_STACK.remove(player.getUUID());
        ATTACK_BUFF_DEADLINE.remove(player.getUUID());
        AttributeInstance attr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attr != null) {
            attr.removeModifier(ATTACK_MODIFIER_ID);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            syncStack(serverPlayer);
        }
    }

    private static void clearPlayer(UUID playerId, ServerPlayer player) {
        PRIMARY_ATTACKS.remove(playerId);
        SETTLED.remove(playerId);
        ATTACK_BUFF_DEADLINE.remove(playerId);
        ATTACK_STACK.remove(playerId);
        STACK_LOCK.remove(playerId);
        BoostStrikeSupport.clearGrace(playerId);
        if (player != null) {
            BoostStrikeSupport.removeReachBonus(player);
            AttributeInstance attr = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attr != null) {
                attr.removeModifier(ATTACK_MODIFIER_ID);
            }
        }
    }

    private static void reassertLockedStack(ServerPlayer player) {
        Double locked = STACK_LOCK.get(player.getUUID());
        if (locked == null) {
            return;
        }
        applyAbsoluteStack(player, locked, /*locked*/ true);
        syncStack(player);
    }

    private static void applyAbsoluteStack(ServerPlayer player, double amount, boolean locked) {
        AttributeInstance attr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attr == null) {
            return;
        }
        double next = Math.max(0.0, amount);
        if (next > 0.0) {
            ATTACK_STACK.put(player.getUUID(), next);
        } else {
            ATTACK_STACK.remove(player.getUUID());
        }
        attr.removeModifier(ATTACK_MODIFIER_ID);
        if (next > 0.0) {
            attr.addTransientModifier(new AttributeModifier(
                    ATTACK_MODIFIER_ID, next, AttributeModifier.Operation.ADD_VALUE));
            if (locked) {
                // 远未来 deadline：tick 循环跳过到期；sync 用锁定 HUD 剩余
                ATTACK_BUFF_DEADLINE.put(player.getUUID(), Long.MAX_VALUE / 4);
            }
        } else {
            ATTACK_BUFF_DEADLINE.remove(player.getUUID());
        }
    }

    private static BoosterTier resolveTier(ServerPlayer player) {
        return BoosterEquipment.find(player)
                .map(equipped -> equipped.item().getTier())
                .orElse(BoosterTier.IRON);
    }

    private static void syncStack(ServerPlayer player) {
        boolean locked = STACK_LOCK.containsKey(player.getUUID());
        double stack = locked
                ? STACK_LOCK.getOrDefault(player.getUUID(), 0.0)
                : ATTACK_STACK.getOrDefault(player.getUUID(), 0.0);
        float maxStack = 0.0f;
        int remaining = 0;
        if (stack > 0.0 || locked) {
            BoosterEquipment.Equipped equipped = BoosterEquipment.find(player).orElse(null);
            if (equipped != null) {
                maxStack = (float) BoostStrikeProfile.forTier(equipped.item().getTier()).maxStackBonus();
            } else {
                maxStack = (float) Math.max(stack, 1.0);
            }
            // 锁定时可超过品质上限（调试用），HUD 至少显示满轨比例不炸
            if (maxStack > 0.0f && stack > maxStack) {
                maxStack = (float) stack;
            }
            if (locked) {
                remaining = LOCKED_HUD_REMAINING_TICKS;
            } else {
                Long deadline = ATTACK_BUFF_DEADLINE.get(player.getUUID());
                if (deadline != null) {
                    remaining = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, deadline - player.server.getTickCount()));
                }
                if (remaining <= 0) {
                    stack = 0.0;
                    maxStack = 0.0f;
                }
            }
        }
        ServerPlayNetworking.send(
                player,
                new BoosterStrikeStackPayload((float) stack, maxStack, remaining));
    }

    private record PrimaryAttack(UUID targetId, int tick, BoosterTier tier, BoosterLeggingsItem boosterItem) {}

    private record SettledKey(UUID targetId, int tick) {}
}
