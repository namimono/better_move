package com.boostermod.item;

import com.boostermod.BoosterMod;
import com.boostermod.balance.BoosterBalanceProfile;
import com.boostermod.charge.OverloadExplosion;
import com.boostermod.combat.BoostStrikeSupport;
import com.boostermod.wallbreak.WallBreakSupport;
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

    /** 过载推进本帧将撞击时，破击应让路。 */
    public static boolean shouldSuppressStrikeForOverloadImpact(ServerPlayer player) {
        ActiveBoost boost = ACTIVE.get(player.getUUID());
        if (boost == null || !boost.overloaded || boost.exploded) {
            return false;
        }
        return OverloadExplosion.isSolidOrEntityImpact(
                boost.level, player, boost.groundLaunch && boost.tick < 2);
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
        start(level, player, direction, profile, originEye, eyeOffsetY, hyper, groundLaunch, false);
    }

    public static void start(
            ServerLevel level,
            ServerPlayer player,
            Vec3 direction,
            BoosterBalanceProfile profile,
            Vec3 originEye,
            double eyeOffsetY,
            boolean hyper,
            boolean groundLaunch,
            boolean overloaded) {
        applyStepHeightBoost(player);
        BoostStrikeSupport.onBoostStart(player);

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

        Vec3 startVelocity = new Vec3(startX, startY, startZ);
        player.setDeltaMovement(startVelocity);
        player.resetFallDistance();
        player.hurtMarked = true;

        boolean wallBreak = !overloaded && WallBreakSupport.isInstalled(player);
        ACTIVE.put(
                player.getUUID(),
                new ActiveBoost(
                        level,
                        direction,
                        profile,
                        originEye,
                        eyeOffsetY,
                        groundLaunch,
                        grantedNoGravity,
                        overloaded,
                        wallBreak,
                        startVelocity));
    }

    public static void tickServer(MinecraftServer server) {
        Iterator<Map.Entry<UUID, ActiveBoost>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ActiveBoost> entry = it.next();
            UUID id = entry.getKey();
            ActiveBoost boost = entry.getValue();
            ServerPlayer player = resolvePlayer(server, boost, id);
            if (player == null) {
                it.remove();
                continue;
            }

            if (boost.step(player)) {
                stopBoost(player, boost);
                it.remove();
            }
        }
    }

    /**
     * 优先走 PlayerList；GameTest FakePlayer 只挂在世界实体里，需回退到 {@code level.getEntity}。
     */
    private static ServerPlayer resolvePlayer(MinecraftServer server, ActiveBoost boost, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) {
            return player;
        }
        var entity = boost.level.getEntity(id);
        return entity instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }

    public static void cancel(ServerPlayer player) {
        ActiveBoost boost = ACTIVE.remove(player.getUUID());
        if (boost != null) {
            stopBoost(player, boost);
        }
    }

    private static void stopBoost(ServerPlayer player, ActiveBoost boost) {
        removeStepHeightBoost(player);
        // 破击宽限：结束后 1s 内仍算破击窗口，触及暂不立刻移除。
        BoostStrikeSupport.onBoostEnd(player);
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
        private final boolean overloaded;
        private final boolean wallBreakEligible;
        private boolean grantedApexNoGravity;
        private boolean exploded;
        private int tick;
        private Vec3 lastAppliedVelocity;

        private ActiveBoost(
                ServerLevel level,
                Vec3 fallbackDirection,
                BoosterBalanceProfile profile,
                Vec3 originEye,
                double eyeOffsetY,
                boolean groundLaunch,
                boolean grantedNoGravity,
                boolean overloaded,
                boolean wallBreakEligible,
                Vec3 startVelocity) {
            this.level = level;
            this.fallbackDirection = fallbackDirection;
            this.profile = profile;
            this.originEye = originEye;
            this.eyeOffsetY = eyeOffsetY;
            this.groundLaunch = groundLaunch;
            this.grantedNoGravity = grantedNoGravity;
            this.overloaded = overloaded;
            this.wallBreakEligible = wallBreakEligible;
            this.lastAppliedVelocity = startVelocity;
        }

        private boolean step(ServerPlayer player) {
            if (overloaded && !exploded && OverloadExplosion.isSolidOrEntityImpact(
                    level, player, groundLaunch && tick < 2)) {
                OverloadExplosion.detonate(level, player);
                exploded = true;
                emitEndParticles(player.position());
                return true;
            }

            if (wallBreakEligible && WallBreakSupport.isInstalled(player)) {
                if (handleWallBreak(player)) {
                    emitEndParticles(player.position());
                    return true;
                }
            } else if (!overloaded && player.horizontalCollision) {
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
            Vec3 next = new Vec3(
                    velocity.x + thrustDirection.x * thrust,
                    velocity.y + thrustDirection.y * thrust,
                    velocity.z + thrustDirection.z * thrust);
            applyVelocity(player, next);

            if (wallBreakEligible && WallBreakSupport.isInstalled(player)) {
                WallBreakSupport.Outcome foresight = WallBreakSupport.clearSweptPath(level, player, next);
                if (foresight == WallBreakSupport.Outcome.HIT_UNBREAKABLE) {
                    emitEndParticles(player.position());
                    return true;
                }
            }

            if (player.onGround()) {
                player.setOnGround(false);
            }
            player.resetFallDistance();
            player.hurtMarked = true;

            tick++;
            return false;
        }

        /**
         * @return true 若破壁路径撞上不可破坏方块，应结束推进
         */
        private boolean handleWallBreak(ServerPlayer player) {
            Vec3 hint = lastAppliedVelocity;
            if (hint.lengthSqr() < 1.0e-6) {
                hint = fallbackDirection;
            }
            // 水平碰撞后速度常被清零：至少沿原推进方向探测一格，避免扫掠退化
            if (player.horizontalCollision && hint.lengthSqr() < 1.0) {
                Vec3 dir = hint.lengthSqr() < 1.0e-6 ? fallbackDirection : hint.normalize();
                hint = dir.scale(1.0);
            }

            WallBreakSupport.Outcome outcome = WallBreakSupport.clearSweptPath(level, player, hint);
            if (outcome == WallBreakSupport.Outcome.HIT_UNBREAKABLE) {
                return true;
            }
            if (outcome == WallBreakSupport.Outcome.CLEARED || player.horizontalCollision) {
                if (outcome == WallBreakSupport.Outcome.CLEARED) {
                    applyVelocity(player, lastAppliedVelocity.lengthSqr() > 1.0e-6
                            ? lastAppliedVelocity
                            : hint);
                    player.horizontalCollision = false;
                } else if (player.horizontalCollision) {
                    // 仍有水平碰撞但未清到方块：按原规则结束
                    return true;
                }
            }
            return false;
        }

        private void applyVelocity(ServerPlayer player, Vec3 velocity) {
            lastAppliedVelocity = velocity;
            player.setDeltaMovement(velocity);
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
