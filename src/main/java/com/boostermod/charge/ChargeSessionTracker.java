package com.boostermod.charge;

import com.boostermod.item.BoosterEquipment;
import com.boostermod.item.BoosterLeggingsItem;
import com.boostermod.item.BoosterMotionTicker;
import com.boostermod.upgrade.BoosterUpgradeHelper;
import com.boostermod.upgrade.BoosterUpgradeType;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 服务端过载蓄力会话适配器：硬拒绝点、强制释放、粒子，以及开火路由。
 */
public final class ChargeSessionTracker {
    private static final int MIN_FOOD_LEVEL = 6;
    private static final double TELEPORT_CANCEL_DISTANCE_SQR = 4.0 * 4.0;
    private static final int HYPER_WINDOW_TICKS = 3;

    private static final Map<UUID, ChargeSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, TrackedPos> LAST_POS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LANDING_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> WAS_ON_GROUND = new ConcurrentHashMap<>();

    private ChargeSessionTracker() {}

    public static boolean isCharging(ServerPlayer player) {
        ChargeSession session = SESSIONS.get(player.getUUID());
        return session != null && session.isActive();
    }

    public static boolean hasChargeUpgrade(Player player) {
        return BoosterEquipment.find(player)
                .map(equipped -> BoosterUpgradeHelper.hasUpgrade(
                        equipped.stack(), BoosterUpgradeType.CHARGE, player.registryAccess()))
                .orElse(false);
    }

    public static void tryStart(ServerPlayer player) {
        if (!canStartCharge(player)) {
            return;
        }
        ChargeSession session = SESSIONS.computeIfAbsent(player.getUUID(), id -> new ChargeSession());
        if (session.tryStart(player.server.getTickCount())) {
            LAST_POS.put(player.getUUID(), TrackedPos.of(player));
        }
    }

    public static void cancel(ServerPlayer player) {
        ChargeSession session = SESSIONS.get(player.getUUID());
        if (session != null && session.isActive()) {
            session.cancel();
        }
        clearTracking(player.getUUID());
    }

    public static void clear(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
        clearTracking(player.getUUID());
    }

    private static void clearTracking(UUID id) {
        LAST_POS.remove(id);
        LANDING_TICK.remove(id);
        WAS_ON_GROUND.remove(id);
    }

    public static void onFireRequest(
            ServerPlayer player,
            double dirX,
            double dirZ,
            int jumpTicksAgo,
            int landingTicksAgo) {
        if (!hasChargeUpgrade(player)) {
            BoosterLeggingsItem.tryBoostFromKey(player, dirX, dirZ, jumpTicksAgo, landingTicksAgo, null);
            return;
        }

        ChargeSession session = SESSIONS.get(player.getUUID());
        if (session == null || !session.isActive()) {
            return;
        }

        boolean canAttemptLaunch = canAttemptLaunch(player);
        ChargeSession.ReleaseResult release =
                session.release(player.server.getTickCount(), canAttemptLaunch);
        clearTracking(player.getUUID());
        if (!release.accepted() || !release.allowLaunchAttempt()) {
            return;
        }

        BoosterLeggingsItem.tryBoostFromKey(
                player,
                dirX,
                dirZ,
                jumpTicksAgo,
                landingTicksAgo,
                new ChargeBoostContext(release.multiplier(), release.overloaded()));
    }

    public static void tickServer(MinecraftServer server) {
        int now = server.getTickCount();
        Iterator<Map.Entry<UUID, ChargeSession>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ChargeSession> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                clearTracking(entry.getKey());
                continue;
            }

            ChargeSession session = entry.getValue();
            if (!session.isActive()) {
                clearTracking(entry.getKey());
                continue;
            }

            trackLanding(player, now);

            boolean cancelRequested = shouldCancelCharge(player);
            ChargeSession.TickResult tick = session.tick(now, cancelRequested);
            if (tick.cancelled() || !tick.active()) {
                clearTracking(entry.getKey());
                continue;
            }

            emitChargeParticles(player, tick.overloaded());
            LAST_POS.put(player.getUUID(), TrackedPos.of(player));

            if (tick.shouldForceRelease()) {
                boolean canAttemptLaunch = canAttemptLaunch(player);
                int landingTicksAgo = landingTicksAgo(player);
                ChargeSession.ReleaseResult release = session.release(now, canAttemptLaunch);
                clearTracking(player.getUUID());
                if (release.accepted() && release.allowLaunchAttempt()) {
                    BoosterLeggingsItem.tryBoostFromKey(
                            player,
                            0.0,
                            0.0,
                            -1,
                            landingTicksAgo,
                            new ChargeBoostContext(release.multiplier(), release.overloaded()));
                }
            }
        }
    }

    private static void trackLanding(ServerPlayer player, int now) {
        boolean onGround = player.onGround();
        Boolean wasOnGround = WAS_ON_GROUND.put(player.getUUID(), onGround);
        if (onGround && Boolean.FALSE.equals(wasOnGround)) {
            LANDING_TICK.put(player.getUUID(), now);
        }
    }

    private static int landingTicksAgo(ServerPlayer player) {
        Integer landedAt = LANDING_TICK.get(player.getUUID());
        if (landedAt == null || !player.onGround()) {
            return -1;
        }
        int ago = player.server.getTickCount() - landedAt;
        return ago <= HYPER_WINDOW_TICKS ? ago : -1;
    }

    private static boolean canStartCharge(ServerPlayer player) {
        if (player.isSpectator() || player.isDeadOrDying() || player.isSleeping()) {
            return false;
        }
        if (BoosterMotionTicker.isBoosting(player)) {
            return false;
        }
        BoosterEquipment.Equipped equipped = BoosterEquipment.find(player).orElse(null);
        if (equipped == null) {
            return false;
        }
        var registries = player.registryAccess();
        ItemStack boosterStack = equipped.stack();
        if (!BoosterUpgradeHelper.hasUpgrade(boosterStack, BoosterUpgradeType.CHARGE, registries)) {
            return false;
        }
        boolean noCooldown = BoosterUpgradeHelper.hasUpgrade(
                boosterStack, BoosterUpgradeType.NO_COOLDOWN, registries);
        if (!noCooldown && player.getCooldowns().isOnCooldown(equipped.item())) {
            return false;
        }
        if (!player.getAbilities().instabuild && player.getFoodData().getFoodLevel() < MIN_FOOD_LEVEL) {
            return false;
        }
        ChargeSession existing = SESSIONS.get(player.getUUID());
        return existing == null || !existing.isActive();
    }

    private static boolean shouldCancelCharge(ServerPlayer player) {
        if (player.isSpectator() || player.isDeadOrDying() || player.isSleeping()) {
            return true;
        }
        if (player.containerMenu != player.inventoryMenu) {
            return true;
        }
        BoosterEquipment.Equipped equipped = BoosterEquipment.find(player).orElse(null);
        if (equipped == null) {
            return true;
        }
        var registries = player.registryAccess();
        if (!BoosterUpgradeHelper.hasUpgrade(equipped.stack(), BoosterUpgradeType.CHARGE, registries)) {
            return true;
        }
        boolean noCooldown = BoosterUpgradeHelper.hasUpgrade(
                equipped.stack(), BoosterUpgradeType.NO_COOLDOWN, registries);
        if (!noCooldown && player.getCooldowns().isOnCooldown(equipped.item())) {
            return true;
        }
        if (!player.getAbilities().instabuild && player.getFoodData().getFoodLevel() < MIN_FOOD_LEVEL) {
            return true;
        }
        TrackedPos last = LAST_POS.get(player.getUUID());
        if (last != null && last.dimension.equals(player.level().dimension())) {
            double dx = player.getX() - last.x;
            double dy = player.getY() - last.y;
            double dz = player.getZ() - last.z;
            if (dx * dx + dy * dy + dz * dz > TELEPORT_CANCEL_DISTANCE_SQR) {
                return true;
            }
        } else if (last != null) {
            return true;
        }
        return false;
    }

    private static boolean canAttemptLaunch(ServerPlayer player) {
        if (player.isSpectator() || player.isDeadOrDying() || player.isSleeping()) {
            return false;
        }
        if (BoosterMotionTicker.isBoosting(player)) {
            return false;
        }
        BoosterEquipment.Equipped equipped = BoosterEquipment.find(player).orElse(null);
        if (equipped == null) {
            return false;
        }
        var registries = player.registryAccess();
        if (!BoosterUpgradeHelper.hasUpgrade(equipped.stack(), BoosterUpgradeType.CHARGE, registries)) {
            return false;
        }
        boolean noCooldown = BoosterUpgradeHelper.hasUpgrade(
                equipped.stack(), BoosterUpgradeType.NO_COOLDOWN, registries);
        if (!noCooldown && player.getCooldowns().isOnCooldown(equipped.item())) {
            return false;
        }
        if (!player.getAbilities().instabuild && player.getFoodData().getFoodLevel() < MIN_FOOD_LEVEL) {
            return false;
        }
        boolean groundLaunch = player.onGround();
        if (!groundLaunch && !BoosterUpgradeHelper.hasUpgrade(
                equipped.stack(), BoosterUpgradeType.AIR_DASH, registries)) {
            return false;
        }
        return true;
    }

    private static void emitChargeParticles(ServerPlayer player, boolean overloaded) {
        ServerLevel level = player.serverLevel();
        double x = player.getX();
        double y = player.getY() + player.getBbHeight() * 0.55;
        double z = player.getZ();
        if (overloaded) {
            level.sendParticles(ParticleTypes.FLAME, x, y, z, 3, 0.2, 0.15, 0.2, 0.01);
            level.sendParticles(ParticleTypes.SMOKE, x, y, z, 2, 0.15, 0.1, 0.15, 0.01);
        } else {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 2, 0.2, 0.15, 0.2, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.12, 0.1, 0.12, 0.0);
        }
    }

    public record ChargeBoostContext(double distanceMultiplier, boolean overloaded) {}

    private record TrackedPos(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                              double x, double y, double z) {
        static TrackedPos of(ServerPlayer player) {
            return new TrackedPos(player.level().dimension(), player.getX(), player.getY(), player.getZ());
        }
    }
}
