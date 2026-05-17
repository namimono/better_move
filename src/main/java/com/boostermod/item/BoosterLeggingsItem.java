package com.boostermod.item;

import com.boostermod.balance.BoosterBalanceManager;
import com.boostermod.balance.BoosterBalanceProfile;
import com.boostermod.network.BoosterFeedbackPayload;
import com.boostermod.screen.BoosterUpgradeMenuProvider;
import com.boostermod.tier.BoosterTier;
import com.boostermod.upgrade.BoosterUpgradeHelper;
import com.boostermod.upgrade.BoosterUpgradeType;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
    private static final int HYPER_WINDOW_TICKS = 3;
    private static final int COOLDOWN_TICKS = 60;
    private static final float MOVEMENT_BOOST_HUNGER_EXHAUSTION = 1.0f;
    private static final float BOOST_SOUND_VOLUME = 1.0f;
    private static final float BOOST_SOUND_PITCH = 1.2f;
    /** 起跳前的最小前向探测距离：若该格内无法容纳玩家，则视为贴墙，不消耗冷却。 */
    private static final double FORWARD_PROBE_DISTANCE = 0.1;
    /** 探测前向时允许上抬的最大高度（与 {@link BoosterMotionTicker} 的 step height 提升保持一致）。 */
    private static final double PROBE_STEP_UP_MAX = 1.0;
    private static final double PROBE_STEP_UP_GRAIN = 0.05;
    private static final int MIN_FOOD_LEVEL = 6;

    private final BoosterTier tier;

    public BoosterLeggingsItem(Properties properties, BoosterTier tier) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.LEGS;
    }

    public BoosterTier getTier() {
        return tier;
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            return this.swapWithEquipmentSlot(this, level, player, hand);
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new BoosterUpgradeMenuProvider(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static void tryBoostFromKey(
            ServerPlayer player,
            double clientDirX,
            double clientDirZ,
            int jumpTicksAgo,
            int landingTicksAgo) {
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
        boolean groundLaunch = player.onGround();
        if (!groundLaunch && !BoosterUpgradeHelper.hasUpgrade(
                legs, BoosterUpgradeType.AIR_DASH, player.registryAccess())) {
            return;
        }

        Vec3 direction = boosterItem.resolveBoostDirection(level, player, groundLaunch);
        if (direction == null) {
            return;
        }

        boolean hyper = isHyperBoost(player, jumpTicksAgo, landingTicksAgo);
        boosterItem.applyBoost(level, player, legs, direction, hyper, groundLaunch);
        ServerPlayNetworking.send(player, new BoosterFeedbackPayload(hyper));
        player.getCooldowns().addCooldown(boosterItem, COOLDOWN_TICKS);
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static boolean isHyperBoost(ServerPlayer player, int jumpTicksAgo, int landingTicksAgo) {
        boolean landingWindow = player.onGround() && landingTicksAgo >= 0 && landingTicksAgo <= HYPER_WINDOW_TICKS;
        return landingWindow;
    }

    public static void tickActiveMotions(MinecraftServer server) {
        BoosterMotionTicker.tickServer(server);
    }

    /**
     * 计算推进方向并做一次性前向碰撞探测：方向非零且前方至少能容纳 {@link #FORWARD_PROBE_DISTANCE} 才放行。
     * 实际飞行距离不再预先扫描，完全由 {@link BoosterMotionTicker} 的物理推力与 MC 引擎决定。
     */
    private Vec3 resolveBoostDirection(Level level, Player player, boolean groundLaunch) {
        Vec3 direction = player.getViewVector(1.0f);
        if (direction.lengthSqr() < 1.0e-6) {
            return null;
        }
        direction = direction.normalize();
        AABB originBox = player.getBoundingBox();
        Vec3 probeDirection = groundLaunch ? horizontalProbeDirection(direction) : direction;
        Vec3 probe = probeDirection.scale(FORWARD_PROBE_DISTANCE);
        if (!hasForwardClearance(level, player, originBox, probe)) {
            return null;
        }
        return direction;
    }

    private static boolean hasForwardClearance(Level level, Player player, AABB originBox, Vec3 probe) {
        if (canFitOffset(level, player, originBox, probe)) {
            return true;
        }
        for (double up = PROBE_STEP_UP_GRAIN; up <= PROBE_STEP_UP_MAX + 1.0e-6; up += PROBE_STEP_UP_GRAIN) {
            if (canFitOffset(level, player, originBox, probe.add(0.0, up, 0.0))) {
                return true;
            }
        }
        return false;
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

    private static Vec3 horizontalProbeDirection(Vec3 direction) {
        Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z);
        if (horizontal.lengthSqr() < 1.0e-6) {
            return direction;
        }
        return horizontal.normalize();
    }

    private void applyBoost(
            ServerLevel level,
            ServerPlayer player,
            ItemStack legsStack,
            Vec3 direction,
            boolean hyper,
            boolean groundLaunch) {
        BoosterBalanceProfile balance = currentBalance(level);
        Vec3 originEye = player.getEyePosition();
        double eyeOffsetY = player.getEyeY() - player.getY();

        player.causeFoodExhaustion(MOVEMENT_BOOST_HUNGER_EXHAUSTION);
        if (!player.getAbilities().instabuild) {
            legsStack.hurtAndBreak(1, player, EquipmentSlot.LEGS);
        }

        level.playSound(null, originEye.x, originEye.y, originEye.z,
                SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, BOOST_SOUND_VOLUME, BOOST_SOUND_PITCH);

        BoosterMotionTicker.start(level, player, direction, balance, originEye, eyeOffsetY, hyper, groundLaunch);
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
