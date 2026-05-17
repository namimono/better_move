package com.boostermod.item;

import com.boostermod.BoosterMod;
import com.boostermod.balance.BoosterBalanceProfile;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

public final class BoosterMotionTicker {
    private static final ResourceLocation STEP_HEIGHT_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(BoosterMod.MOD_ID, "boost_step_height");
    private static final double STEP_HEIGHT_BOOST = 0.5;
    private static final double GROUND_JUMP_KICK = 0.42;
    private static final double HYPER_IMPULSE_MULTIPLIER = 1.75;
    private static final Map<UUID, ActiveBoost> ACTIVE = new ConcurrentHashMap<>();

    private BoosterMotionTicker() {}

    public static boolean isBoosting(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static void setSteerInput(UUID playerId, float strafe, float forward) {
        // Boost steering now follows look direction only, so movement-key steer input is ignored.
    }

    public static void start(
            ServerLevel level,
            ServerPlayer player,
            Vec3 direction,
            BoosterBalanceProfile profile,
            Vec3 originEye,
            double eyeOffsetY,
            boolean hyper,
            boolean groundLaunch) {
        applyStepHeightBoost(player);

        Vec3 existingVelocity = player.getDeltaMovement();
        boolean grantedNoGravity = !groundLaunch && !player.isNoGravity();
        if (grantedNoGravity) {
            player.setNoGravity(true);
        }
        player.setOnGround(false);

        double impulse = profile.impulse() * (hyper ? HYPER_IMPULSE_MULTIPLIER : 1.0);
        double startX = hyper ? existingVelocity.x + direction.x * impulse : direction.x * impulse;
        double startY = hyper ? existingVelocity.y + direction.y * impulse : direction.y * impulse;
        double startZ = hyper ? existingVelocity.z + direction.z * impulse : direction.z * impulse;
        if (groundLaunch) {
            // Ensure the first tick escapes ground friction even if the player is looking level or down.
            startY = Math.max(GROUND_JUMP_KICK, startY);
        }

        player.setDeltaMovement(startX, startY, startZ);
        player.resetFallDistance();
        player.hurtMarked = true;

        ACTIVE.put(player.getUUID(), new ActiveBoost(level, direction, profile, originEye, eyeOffsetY, groundLaunch, grantedNoGravity));
    }

    public static void tickServer(MinecraftServer server) {
        Iterator<Map.Entry<UUID, ActiveBoost>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ActiveBoost> entry = it.next();
            UUID id = entry.getKey();
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                it.remove();
                continue;
            }

            ActiveBoost boost = entry.getValue();
            if (boost.step(player)) {
                stopBoost(player, boost);
                it.remove();
            }
        }
    }

    public static void cancel(ServerPlayer player) {
        ActiveBoost boost = ACTIVE.remove(player.getUUID());
        if (boost != null) {
            stopBoost(player, boost);
        }
    }

    private static void stopBoost(ServerPlayer player, ActiveBoost boost) {
        removeStepHeightBoost(player);
        if (boost.grantedNoGravity || boost.grantedApexNoGravity) {
            player.setNoGravity(false);
        }
    }

    private static void applyStepHeightBoost(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.STEP_HEIGHT);
        if (attr == null || attr.getModifier(STEP_HEIGHT_MODIFIER_ID) != null) {
            return;
        }
        attr.addTransientModifier(new AttributeModifier(
                STEP_HEIGHT_MODIFIER_ID, STEP_HEIGHT_BOOST, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void removeStepHeightBoost(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.STEP_HEIGHT);
        if (attr != null) {
            attr.removeModifier(STEP_HEIGHT_MODIFIER_ID);
        }
    }

    private static final class ActiveBoost {
        private final ServerLevel level;
        private final Vec3 fallbackDirection;
        private final BoosterBalanceProfile profile;
        private final Vec3 originEye;
        private final double eyeOffsetY;
        private final boolean groundLaunch;
        private final boolean grantedNoGravity;
        private boolean grantedApexNoGravity;
        private int tick;

        private ActiveBoost(
                ServerLevel level,
                Vec3 fallbackDirection,
                BoosterBalanceProfile profile,
                Vec3 originEye,
                double eyeOffsetY,
                boolean groundLaunch,
                boolean grantedNoGravity) {
            this.level = level;
            this.fallbackDirection = fallbackDirection;
            this.profile = profile;
            this.originEye = originEye;
            this.eyeOffsetY = eyeOffsetY;
            this.groundLaunch = groundLaunch;
            this.grantedNoGravity = grantedNoGravity;
        }

        private boolean step(ServerPlayer player) {
            if (player.horizontalCollision) {
                emitEndParticles(player.position());
                return true;
            }

            int totalTicks = profile.thrustTicks();
            if (totalTicks <= 0 || tick >= totalTicks) {
                emitEndParticles(player.position());
                return true;
            }

            double progress = (double) tick / totalTicks;
            double thrust = profile.thrustPerTick() * (1.0 - progress);
            Vec3 look = player.getViewVector(1.0f);
            Vec3 thrustDirection = look.lengthSqr() < 1.0e-6 ? fallbackDirection : look.normalize();
            Vec3 velocity = maybeSuppressGravityAtApex(player, player.getDeltaMovement());
            player.setDeltaMovement(
                    velocity.x + thrustDirection.x * thrust,
                    velocity.y + thrustDirection.y * thrust,
                    velocity.z + thrustDirection.z * thrust);

            if (player.onGround()) {
                player.setOnGround(false);
            }
            player.resetFallDistance();
            player.hurtMarked = true;

            tick++;
            return false;
        }

        private Vec3 maybeSuppressGravityAtApex(ServerPlayer player, Vec3 velocity) {
            if (!groundLaunch || grantedApexNoGravity || tick == 0 || velocity.y > 0.0 || player.isNoGravity()) {
                return velocity;
            }

            grantedApexNoGravity = true;
            player.setNoGravity(true);
            return new Vec3(velocity.x, 0.0, velocity.z);
        }

        private void emitEndParticles(Vec3 currentFeet) {
            Vec3 targetEye = currentFeet.add(0.0, eyeOffsetY, 0.0);
            BoosterLeggingsItem.emitTrailParticles(level, originEye, targetEye);
        }
    }
}
