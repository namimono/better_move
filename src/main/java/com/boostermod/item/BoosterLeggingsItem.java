package com.boostermod.item;

import com.boostermod.balance.BoosterBalanceManager;
import com.boostermod.balance.BoosterBalanceProfile;
import com.boostermod.network.BoosterRequestPayload;
import com.boostermod.tier.BoosterTier;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class BoosterLeggingsItem extends Item implements Equipable {
    private static final int COOLDOWN_TICKS = 60;
    private static final float MOVEMENT_BOOST_HUNGER_EXHAUSTION = 1.0f;
    private static final float LOOK_BOOST_HUNGER_EXHAUSTION = 2.0f;
    private static final double SCAN_STEP = 0.25;
    private static final int REFINE_ITERS = 5;
    private static final double MIN_BOOST_DISTANCE = 0.1;
    private static final double STEP_UP_MAX = 1.0;
    private static final double STEP_UP_GRAIN = 0.05;
    private static final int MIN_FOOD_LEVEL = 6;
    private static final double MIN_CLIENT_DIRECTION_SQR = 1.0e-4;
    private static final BoostMode ACTIVE_BOOST_MODE = BoostMode.MOVEMENT_DIRECTION;

    private final BoosterTier tier;

    private enum BoostMode {
        MOVEMENT_DIRECTION,
        LOOK_DIRECTION
    }

    public BoosterLeggingsItem(Properties properties, BoosterTier tier) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.LEGS;
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return this.swapWithEquipmentSlot(this, level, player, hand);
    }

    public static void tryBoostFromKey(ServerPlayer player, double clientDirX, double clientDirZ) {
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        if (!(legs.getItem() instanceof BoosterLeggingsItem boosterItem)) {
            return;
        }
        if (BoosterMotionTicker.isBoosting(player)) {
            return;
        }
        if (player.getCooldowns().isOnCooldown(boosterItem)) {
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        if (!creative && player.getFoodData().getFoodLevel() < MIN_FOOD_LEVEL) {
            return;
        }

        ServerLevel level = player.serverLevel();
        Vec3 startFeet = player.position();
        Vec3 targetFeet = boosterItem.findBoostTarget(level, player, clientDirX, clientDirZ);
        if (targetFeet == null) {
            return;
        }

        boosterItem.applyBoost(level, player, legs, startFeet, targetFeet);
        player.getCooldowns().addCooldown(boosterItem, COOLDOWN_TICKS);
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    public static void tickActiveMotions(MinecraftServer server) {
        BoosterMotionTicker.tickServer(server);
    }

    private Vec3 findBoostTarget(Level level, Player player, double clientDirX, double clientDirZ) {
        return switch (ACTIVE_BOOST_MODE) {
            case MOVEMENT_DIRECTION -> findMovementBoostTarget(level, player, clientDirX, clientDirZ);
            case LOOK_DIRECTION -> findLookBoostTarget(level, player);
        };
    }

    private Vec3 findMovementBoostTarget(Level level, Player player, double clientDirX, double clientDirZ) {
        Vec3 direction = horizontalBoostDirection(player, clientDirX, clientDirZ);
        if (direction.lengthSqr() < 1.0e-6) {
            return null;
        }

        AABB originBox = player.getBoundingBox();
        Vec3 origin = player.position();
        double maxDistance = currentBalance((ServerLevel) level).distance();
        double bestProgress = 0.0;
        Vec3 bestOffset = Vec3.ZERO;
        boolean blocked = false;

        for (double distance = SCAN_STEP; distance <= maxDistance + 1.0e-6; distance += SCAN_STEP) {
            Vec3 fitted = tryFitOffset(level, player, originBox, direction.scale(distance));
            if (fitted != null) {
                bestProgress = distance;
                bestOffset = fitted;
            } else {
                blocked = true;
                break;
            }
        }

        if (blocked) {
            double low = bestProgress;
            double high = Math.min(bestProgress + SCAN_STEP, maxDistance);
            for (int i = 0; i < REFINE_ITERS; i++) {
                double mid = (low + high) * 0.5;
                Vec3 fitted = tryFitOffset(level, player, originBox, direction.scale(mid));
                if (fitted != null) {
                    low = mid;
                    bestOffset = fitted;
                } else {
                    high = mid;
                }
            }
            bestProgress = low;
        }

        if (bestProgress < MIN_BOOST_DISTANCE) {
            return null;
        }
        return origin.add(bestOffset);
    }

    private Vec3 findLookBoostTarget(Level level, Player player) {
        Vec3 lookVector = player.getViewVector(1.0f);
        if (lookVector.lengthSqr() < 1.0e-6) {
            return null;
        }
        Vec3 direction = lookVector.normalize();

        AABB originBox = player.getBoundingBox();
        Vec3 origin = player.position();
        double maxDistance = currentBalance((ServerLevel) level).distance();
        double bestProgress = 0.0;
        Vec3 bestOffset = Vec3.ZERO;
        boolean blocked = false;

        for (double distance = SCAN_STEP; distance <= maxDistance + 1.0e-6; distance += SCAN_STEP) {
            Vec3 fitted = tryFitOffset(level, player, originBox, direction.scale(distance));
            if (fitted != null) {
                bestProgress = distance;
                bestOffset = fitted;
            } else {
                blocked = true;
                break;
            }
        }

        if (blocked) {
            double low = bestProgress;
            double high = Math.min(bestProgress + SCAN_STEP, maxDistance);
            for (int i = 0; i < REFINE_ITERS; i++) {
                double mid = (low + high) * 0.5;
                Vec3 fitted = tryFitOffset(level, player, originBox, direction.scale(mid));
                if (fitted != null) {
                    low = mid;
                    bestOffset = fitted;
                } else {
                    high = mid;
                }
            }
            bestProgress = low;
        }

        if (bestProgress < MIN_BOOST_DISTANCE) {
            return null;
        }
        return origin.add(bestOffset);
    }

    private static Vec3 tryFitOffset(Level level, Player player, AABB originBox, Vec3 nominal) {
        if (canFitOffset(level, player, originBox, nominal)) {
            return nominal;
        }
        for (double up = STEP_UP_GRAIN; up <= STEP_UP_MAX + 1.0e-6; up += STEP_UP_GRAIN) {
            Vec3 lifted = nominal.add(0.0, up, 0.0);
            if (canFitOffset(level, player, originBox, lifted)) {
                return lifted;
            }
        }
        return null;
    }

    private static boolean canFitOffset(Level level, Player player, AABB originBox, Vec3 offset) {
        AABB movedBox = originBox.move(offset.x, offset.y, offset.z);
        if (!level.getWorldBorder().isWithinBounds(movedBox)) {
            return false;
        }
        if (level.getBlockCollisions(player, movedBox).iterator().hasNext()) {
            return false;
        }
        return level.getEntityCollisions(player, movedBox).isEmpty();
    }

    private static Vec3 horizontalBoostDirection(Player player, double clientDirX, double clientDirZ) {
        Vec3 horizontalMove = new Vec3(clientDirX, 0.0, clientDirZ);
        if (horizontalMove.lengthSqr() > MIN_CLIENT_DIRECTION_SQR) {
            return horizontalMove.normalize();
        }
        Vec3 look = player.getViewVector(1.0f);
        Vec3 horizontalLook = new Vec3(look.x, 0.0, look.z);
        if (horizontalLook.lengthSqr() < 1.0e-6) {
            return horizontalFacingDirection(player);
        }
        return horizontalLook.normalize();
    }

    private static Vec3 horizontalFacingDirection(Player player) {
        double yawRad = Math.toRadians(player.getYRot());
        return new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad)).normalize();
    }

    private void applyBoost(
            ServerLevel level,
            ServerPlayer player,
            ItemStack legsStack,
            Vec3 startFeet,
            Vec3 targetFeet) {
        switch (ACTIVE_BOOST_MODE) {
            case MOVEMENT_DIRECTION -> applyMovementBoost(level, player, legsStack, startFeet, targetFeet);
            case LOOK_DIRECTION -> applyLookBoost(level, player, legsStack, targetFeet);
        }
    }

    private void applyMovementBoost(
            ServerLevel level,
            ServerPlayer player,
            ItemStack legsStack,
            Vec3 startFeet,
            Vec3 targetFeet) {
        BoosterBalanceProfile balance = currentBalance(level);
        Vec3 originEye = player.getEyePosition();
        double eyeOffsetY = player.getEyeY() - player.getY();

        player.causeFoodExhaustion(MOVEMENT_BOOST_HUNGER_EXHAUSTION);
        if (!player.getAbilities().instabuild) {
            legsStack.hurtAndBreak(1, player, EquipmentSlot.LEGS);
        }

        level.playSound(null, originEye.x, originEye.y, originEye.z,
                SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.0f, 1.2f);

        double dx = targetFeet.x - startFeet.x;
        double dz = targetFeet.z - startFeet.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDistance < MIN_BOOST_DISTANCE) {
            return;
        }
        Vec3 boostDirection = new Vec3(dx / horizontalDistance, 0.0, dz / horizontalDistance);
        BoosterMotionTicker.start(
                level,
                player,
                startFeet,
                horizontalDistance,
                boostDirection,
                balance,
                originEye,
                eyeOffsetY
        );
    }

    private void applyLookBoost(ServerLevel level, ServerPlayer player, ItemStack legsStack, Vec3 targetFeet) {
        Vec3 originEye = player.getEyePosition();
        double eyeOffsetY = player.getEyeY() - player.getY();

        player.teleportTo(targetFeet.x, targetFeet.y, targetFeet.z);
        player.resetFallDistance();

        player.causeFoodExhaustion(LOOK_BOOST_HUNGER_EXHAUSTION);
        if (!player.getAbilities().instabuild) {
            legsStack.hurtAndBreak(1, player, EquipmentSlot.LEGS);
        }

        level.playSound(null, originEye.x, originEye.y, originEye.z,
                SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.0f, 1.2f);

        Vec3 targetEye = targetFeet.add(0.0, eyeOffsetY, 0.0);
        emitTrailParticles(level, originEye, targetEye);
    }

    static void emitTrailParticles(ServerLevel level, Vec3 from, Vec3 to) {
        double distance = from.distanceTo(to);
        int count = Math.max(4, (int) (distance * 4));
        for (int i = 0; i < count; i++) {
            double t = (double) i / count;
            Vec3 point = from.lerp(to, t);
            level.sendParticles(ParticleTypes.CLOUD, point.x, point.y, point.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    private BoosterBalanceProfile currentBalance(ServerLevel level) {
        return BoosterBalanceManager.get(level.getServer()).getProfile(tier);
    }
}
