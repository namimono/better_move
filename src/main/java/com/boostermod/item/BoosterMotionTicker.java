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
    /** 破壁资格：原推进方向速度低于此值视为基本耗尽。 */
    private static final double WALL_BREAK_SPEED_EXHAUSTED = 0.12;
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

        // 过载与破壁可并存：破壁期间持续真实过载爆炸，不再因过载关闭破壁资格。
        boolean wallBreak = WallBreakSupport.isInstalled(player);
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
        private boolean insideWallBreak;
        private Vec3 wallEntryVelocity = Vec3.ZERO;
        private boolean hasLeftGround;
        /** 本段连续墙体内已累计的实际清方 tick（进入时已结算一次代价后从 0 计）。 */
        private int wallBreakClearTicks;

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
            if (!player.isAlive() || player.isDeadOrDying() || player.getHealth() <= 0.0f) {
                clearWallBreakState();
                emitEndParticles(player.position());
                return true;
            }

            boolean wallBreakActive = wallBreakEligible && WallBreakSupport.isInstalled(player);
            if (!wallBreakActive) {
                // 卸装 / 移除破壁升级项：立即清除破壁状态，不再保速或计伤害
                clearWallBreakState();
            }

            // 无破壁资格的过载：首次固体/实体撞击爆炸并结束。
            // 有破壁时爆炸由破壁清方路径持续触发，不在此截停推进。
            if (overloaded
                    && !wallBreakActive
                    && !exploded
                    && OverloadExplosion.isSolidOrEntityImpact(
                            level, player, groundLaunch && tick < 2)) {
                OverloadExplosion.detonate(level, player);
                exploded = true;
                emitEndParticles(player.position());
                return true;
            }

            if (wallBreakActive) {
                if (handleWallBreak(player)) {
                    emitEndParticles(player.position());
                    return true;
                }
                if (shouldEndWallBreakEligibility(player)) {
                    emitEndParticles(player.position());
                    return true;
                }
            } else if (!overloaded && player.horizontalCollision) {
                emitEndParticles(player.position());
                return true;
            }

            int totalTicks = profile.thrustTicks();
            boolean thrustActive = totalTicks > 0 && tick < totalTicks;
            if (!thrustActive && !wallBreakActive) {
                emitEndParticles(player.position());
                return true;
            }

            if (wallBreakActive && insideWallBreak) {
                // 墙内：维持进入时前向速度，推力与视线不得改写破壁方向
                applyVelocity(player, wallEntryVelocity);
                if (hitsUnbreakableAhead(player, wallEntryVelocity)) {
                    emitEndParticles(player.position());
                    return true;
                }
            } else if (thrustActive) {
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

                if (wallBreakActive && hitsUnbreakableAhead(player, next)) {
                    emitEndParticles(player.position());
                    return true;
                }
            } else {
                // 破壁资格下的空气滑行：跟随实体阻力，扫掠用当前飞行速度
                if (!player.horizontalCollision) {
                    lastAppliedVelocity = player.getDeltaMovement();
                }
                if (hitsUnbreakableAhead(player, flightHint(player))) {
                    emitEndParticles(player.position());
                    return true;
                }
            }

            if (player.onGround()) {
                if (insideWallBreak || !hasLeftGround) {
                    // 墙内或尚未离地：抬离地面避免摩擦；已离地后的贴地由 shouldEnd 按落地处理
                    player.setOnGround(false);
                }
            } else {
                hasLeftGround = true;
            }
            player.resetFallDistance();
            player.hurtMarked = true;

            tick++;
            return false;
        }

        private boolean shouldEndWallBreakEligibility(ServerPlayer player) {
            if (insideWallBreak) {
                return false;
            }
            if (!hasLeftGround) {
                return false;
            }
            // 落地：结束破壁资格（与速度耗尽为或关系）
            if (player.onGround()) {
                return true;
            }
            Vec3 motion = player.horizontalCollision ? lastAppliedVelocity : player.getDeltaMovement();
            double along = motion.dot(fallbackDirection);
            return along < WALL_BREAK_SPEED_EXHAUSTED;
        }

        private boolean hitsUnbreakableAhead(ServerPlayer player, Vec3 motion) {
            // 只探测，不在此清方，避免绕过破壁伤害结算
            return WallBreakSupport.probeSweptPath(level, player, motion)
                    == WallBreakSupport.Outcome.HIT_UNBREAKABLE;
        }

        /**
         * @return true 若应结束推进（不可破坏方块、破壁致死等）
         */
        private boolean handleWallBreak(ServerPlayer player) {
            Vec3 flight = flightVelocity(player);
            // 当前位置已不嵌在墙内，且紧邻前方也无阻挡 → 已离开本段墙体（空气间隔），重置代价计时
            if (insideWallBreak
                    && !WallBreakSupport.intersectsBreakableCollision(level, player)
                    && !WallBreakSupport.hasBreakableImmediatelyAhead(level, player, flight, 0.25)) {
                clearWallBreakState();
            }

            Vec3 sweepHint = sweepHint(flight, player.horizontalCollision);

            WallBreakSupport.Outcome outcome = WallBreakSupport.clearSweptPath(level, player, sweepHint);
            if (outcome == WallBreakSupport.Outcome.HIT_UNBREAKABLE) {
                // 过载+破壁：不可破坏边界上再炸一次后结束
                if (overloaded) {
                    detonateOverload(player);
                }
                clearWallBreakState();
                return true;
            }
            if (outcome == WallBreakSupport.Outcome.CLEARED) {
                if (!insideWallBreak) {
                    // 保速用真实飞行速度，不用扫掠补长后的 hint
                    wallEntryVelocity = captureForwardVelocity(flight);
                    insideWallBreak = true;
                    wallBreakClearTicks = 0;
                    // 进入墙体：过载时立即真实爆炸一次
                    if (overloaded) {
                        detonateOverload(player);
                    }
                    if (WallBreakSupport.applyHealthCost(player)) {
                        clearWallBreakState();
                        return true;
                    }
                } else {
                    wallBreakClearTicks++;
                    if (wallBreakClearTicks >= WallBreakSupport.COST_INTERVAL_TICKS) {
                        wallBreakClearTicks = 0;
                        // 持续破壁：与生命代价同节奏再次过载爆炸
                        if (overloaded) {
                            detonateOverload(player);
                        }
                        if (WallBreakSupport.applyHealthCost(player)) {
                            clearWallBreakState();
                            return true;
                        }
                    }
                }
                applyVelocity(player, wallEntryVelocity);
                player.horizontalCollision = false;
                return false;
            }

            if (insideWallBreak) {
                // 本 tick 未破坏且无水平碰撞：视为已离开连续墙体，停止保速并重置伤害计时
                if (player.horizontalCollision) {
                    applyVelocity(player, wallEntryVelocity);
                    player.horizontalCollision = false;
                } else {
                    clearWallBreakState();
                }
            } else if (player.horizontalCollision) {
                // 仍有水平碰撞但未清到方块：过载时炸一次后结束
                if (overloaded) {
                    detonateOverload(player);
                }
                return true;
            } else if (overloaded && !exploded && OverloadExplosion.hitsEntity(level, player)) {
                // 破壁路径上空中撞实体：炸一次，不结束推进（墙段离开后可再炸）
                detonateOverload(player);
            }
            return false;
        }

        private void detonateOverload(ServerPlayer player) {
            OverloadExplosion.detonate(level, player);
            exploded = true;
        }

        private void clearWallBreakState() {
            insideWallBreak = false;
            wallEntryVelocity = Vec3.ZERO;
            wallBreakClearTicks = 0;
            // 离开墙体后允许下一段破壁再次过载爆炸
            if (overloaded) {
                exploded = false;
            }
        }

        private Vec3 flightVelocity(ServerPlayer player) {
            Vec3 motion = lastAppliedVelocity;
            if (!player.horizontalCollision && player.getDeltaMovement().lengthSqr() > 1.0e-6) {
                motion = player.getDeltaMovement();
            }
            if (motion.lengthSqr() < 1.0e-6) {
                motion = fallbackDirection;
            }
            return motion;
        }

        /** 扫掠用：碰撞后速度常被清零时至少沿方向探测一格，避免漏清。 */
        private Vec3 sweepHint(Vec3 flight, boolean horizontalCollision) {
            if (horizontalCollision && flight.lengthSqr() < 1.0) {
                Vec3 dir = flight.lengthSqr() < 1.0e-6 ? fallbackDirection : flight.normalize();
                return dir.scale(1.0);
            }
            return flight;
        }

        /** 记录进入墙体时的前向速度，剥离起跳踢腿等无关竖直分量。 */
        private Vec3 captureForwardVelocity(Vec3 hint) {
            Vec3 forward = fallbackDirection.lengthSqr() > 1.0e-6
                    ? fallbackDirection.normalize()
                    : (hint.lengthSqr() > 1.0e-6 ? hint.normalize() : new Vec3(0.0, 0.0, 1.0));
            double along = hint.dot(forward);
            if (along < 0.05) {
                along = Math.max(0.05, Math.sqrt(hint.x * hint.x + hint.z * hint.z));
            }
            return forward.scale(along);
        }

        private Vec3 flightHint(ServerPlayer player) {
            return sweepHint(flightVelocity(player), player.horizontalCollision);
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
