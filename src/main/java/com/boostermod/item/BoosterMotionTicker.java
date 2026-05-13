package com.boostermod.item;

import com.boostermod.balance.BoosterBalanceProfile;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class BoosterMotionTicker {
    private static final double STUCK_PROGRESS = 0.05;
    private static final int STUCK_GRACE_TICKS = 2;
    private static final int STUCK_CONSECUTIVE_TICKS = 3;
    private static final int MAX_TICK_PADDING = 6;

    private static final Map<UUID, ActiveBoost> ACTIVE = new ConcurrentHashMap<>();

    private BoosterMotionTicker() {}

    public static boolean isBoosting(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static void start(
            ServerLevel level,
            ServerPlayer player,
            Vec3 startFeet,
            double targetDistance,
            Vec3 direction,
            BoosterBalanceProfile profile,
            Vec3 originEye,
            double eyeOffsetY) {
        double speed = profile.speed();
        int plannedTicks = estimatePlannedTicks(targetDistance, speed);
        applyVelocity(player, direction, speedForTick(profile, tickProgress(0, plannedTicks)));
        ACTIVE.put(
                player.getUUID(),
                new ActiveBoost(level, startFeet, direction, targetDistance, profile, plannedTicks, originEye, eyeOffsetY));
    }

    public static void tickServer(MinecraftServer server) {
        Iterator<Map.Entry<UUID, ActiveBoost>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ActiveBoost> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            if (entry.getValue().step(player)) {
                stopBoost(player);
                it.remove();
            }
        }
    }

    private static void applyVelocity(ServerPlayer player, Vec3 direction, double speed) {
        player.setDeltaMovement(direction.x * speed, 0.0, direction.z * speed);
        player.resetFallDistance();
        player.hurtMarked = true;
    }

    private static void stopBoost(ServerPlayer player) {
        Vec3 current = player.getDeltaMovement();
        player.setDeltaMovement(0.0, current.y, 0.0);
        player.hurtMarked = true;
    }

    private static final class ActiveBoost {
        private final ServerLevel level;
        private final Vec3 startFeet;
        private final Vec3 direction;
        private final double targetDistance;
        private final BoosterBalanceProfile profile;
        private final int plannedTicks;
        private final Vec3 originEye;
        private final double eyeOffsetY;
        private double lastProgress;
        private int stalledTicks;
        private int tick;

        private ActiveBoost(
                ServerLevel level,
                Vec3 startFeet,
                Vec3 direction,
                double targetDistance,
                BoosterBalanceProfile profile,
                int plannedTicks,
                Vec3 originEye,
                double eyeOffsetY) {
            this.level = level;
            this.startFeet = startFeet;
            this.direction = direction;
            this.targetDistance = targetDistance;
            this.profile = profile;
            this.plannedTicks = plannedTicks;
            this.originEye = originEye;
            this.eyeOffsetY = eyeOffsetY;
        }

        private boolean step(ServerPlayer player) {
            tick++;
            Vec3 current = player.position();
            double progress = forwardProgress(startFeet, current, direction);

            if (tick > STUCK_GRACE_TICKS) {
                if (progress - lastProgress < STUCK_PROGRESS) {
                    stalledTicks++;
                    if (stalledTicks >= STUCK_CONSECUTIVE_TICKS) {
                        emitEndParticles(current);
                        return true;
                    }
                } else {
                    stalledTicks = 0;
                }
            }
            lastProgress = progress;

            if (progress >= targetDistance - 0.05) {
                emitEndParticles(current);
                return true;
            }

            if (tick >= plannedTicks + MAX_TICK_PADDING) {
                emitEndParticles(current);
                return true;
            }

            applyVelocity(player, direction, speedForTick(profile, tickProgress(tick, plannedTicks)));
            return false;
        }

        private void emitEndParticles(Vec3 current) {
            Vec3 targetEye = current.add(0.0, eyeOffsetY, 0.0);
            BoosterLeggingsItem.emitTrailParticles(level, originEye, targetEye);
        }

        private static double forwardProgress(Vec3 startFeet, Vec3 currentFeet, Vec3 direction) {
            double dx = currentFeet.x - startFeet.x;
            double dz = currentFeet.z - startFeet.z;
            return dx * direction.x + dz * direction.z;
        }
    }

    private static int estimatePlannedTicks(double targetDistance, double speed) {
        return Math.max(4, (int) Math.ceil(targetDistance / Math.max(speed, 1.0e-6)));
    }

    private static double tickProgress(int tick, int plannedTicks) {
        if (plannedTicks <= 1) {
            return 1.0;
        }
        return Math.min(1.0, (double) tick / (plannedTicks - 1));
    }

    private static double speedForTick(BoosterBalanceProfile profile, double progress) {
        return profile.speed() * jetSpeedMultiplier(progress, profile);
    }

    private static double jetSpeedMultiplier(double progress, BoosterBalanceProfile profile) {
        double peakMultiplier = profile.boostStrength();
        double endMultiplier = profile.endSpeedMultiplier();
        if (progress < 0.15) {
            return lerp(progress / 0.15, 0.90, peakMultiplier);
        }
        if (progress < 0.70) {
            return lerp((progress - 0.15) / 0.55, peakMultiplier, 1.00);
        }
        return lerp((progress - 0.70) / 0.30, 1.00, endMultiplier);
    }

    private static double lerp(double t, double start, double end) {
        return start + (end - start) * t;
    }
}
