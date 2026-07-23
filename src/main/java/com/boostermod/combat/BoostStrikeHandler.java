package com.boostermod.combat;

import com.boostermod.BoosterMod;
import com.boostermod.item.BoosterEquipment;
import com.boostermod.item.BoosterLeggingsItem;
import com.boostermod.network.BoosterStrikeFeedbackPayload;
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
            if (tick < entry.getValue()) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                clearAttackStack(player);
            } else {
                ATTACK_STACK.remove(entry.getKey());
            }
            buffIt.remove();
        }
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
        ATTACK_STACK.put(player.getUUID(), next);

        attr.removeModifier(ATTACK_MODIFIER_ID);
        if (next > 0.0) {
            attr.addTransientModifier(new AttributeModifier(
                    ATTACK_MODIFIER_ID, next, AttributeModifier.Operation.ADD_VALUE));
            long deadline = player.server.getTickCount() + (long) durationTicks;
            ATTACK_BUFF_DEADLINE.put(player.getUUID(), deadline);
        } else {
            ATTACK_BUFF_DEADLINE.remove(player.getUUID());
        }
    }

    private static void resetBoosterCooldown(ServerPlayer player, BoosterLeggingsItem boosterItem) {
        player.getCooldowns().removeCooldown(boosterItem);
    }

    private static void clearAttackStack(Player player) {
        ATTACK_STACK.remove(player.getUUID());
        ATTACK_BUFF_DEADLINE.remove(player.getUUID());
        AttributeInstance attr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attr != null) {
            attr.removeModifier(ATTACK_MODIFIER_ID);
        }
    }

    private static void clearPlayer(UUID playerId, ServerPlayer player) {
        PRIMARY_ATTACKS.remove(playerId);
        SETTLED.remove(playerId);
        ATTACK_BUFF_DEADLINE.remove(playerId);
        ATTACK_STACK.remove(playerId);
        BoostStrikeSupport.clearGrace(playerId);
        if (player != null) {
            BoostStrikeSupport.removeReachBonus(player);
            AttributeInstance attr = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attr != null) {
                attr.removeModifier(ATTACK_MODIFIER_ID);
            }
        }
    }

    private record PrimaryAttack(UUID targetId, int tick, BoosterTier tier, BoosterLeggingsItem boosterItem) {}

    private record SettledKey(UUID targetId, int tick) {}
}
