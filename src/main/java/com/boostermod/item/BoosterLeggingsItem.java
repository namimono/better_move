package com.boostermod.item;

import com.boostermod.balance.BoosterBalanceManager;
import com.boostermod.balance.BoosterBalanceProfile;
import com.boostermod.charge.ChargeSessionTracker;
import com.boostermod.charge.OverloadExplosion;
import com.boostermod.network.BoosterFeedbackPayload;
import com.boostermod.screen.BoosterUpgradeMenuProvider;
import com.boostermod.tier.BoosterTier;
import com.boostermod.upgrade.BoosterUpgradeHelper;
import com.boostermod.upgrade.BoosterUpgradeType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class BoosterLeggingsItem extends Item implements Equipable {
    private static final int HYPER_WINDOW_TICKS = 3;
    private static final int COOLDOWN_TICKS = 60;
    private static final float MOVEMENT_BOOST_HUNGER_EXHAUSTION = 1.0f;
    private static final float BOOST_SOUND_VOLUME = 1.0f;
    private static final float BOOST_SOUND_PITCH = 1.2f;
    private static final int MIN_FOOD_LEVEL = 6;
    private static final int BURROW_DEPTH_BLOCKS = 6;
    /** 大致朝下才遁地；越大越不容易误触（90° 为竖直向下）。 */
    private static final float BURROW_LOOK_DOWN_PITCH_DEG = 60.0f;
    private static final double RANDOM_IMPULSE_MIN = 0.1;
    private static final double RANDOM_IMPULSE_MAX = 1.0;
    private static final double BOX_EDGE_EPSILON = 1.0e-7;
    private static final Vec3 VERTICAL_LAUNCH_DIRECTION = new Vec3(0.0, 1.0, 0.0);

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
            if (!level.isClientSide && BoosterEquipment.tryEquipToTrinketSlot(player, stack)) {
                return InteractionResultHolder.success(stack);
            }
            return this.swapWithEquipmentSlot(this, level, player, hand);
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new BoosterUpgradeMenuProvider(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var registries = context.registries();
        if (registries == null) {
            return;
        }
        List<BoosterUpgradeType> types = BoosterUpgradeHelper.listUpgradeTypes(stack, registries);
        if (types.isEmpty()) {
            tooltip.add(Component.translatable("item.boostermod.booster_leggings.no_upgrades")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.boostermod.booster_leggings.upgrades_header")
                .withStyle(ChatFormatting.GRAY));
        for (BoosterUpgradeType type : types) {
            tooltip.add(Component.literal(" - ")
                    .append(Component.translatable("item.boostermod." + type.getItemId()))
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    public static void tryBoostFromKey(
            ServerPlayer player,
            double clientDirX,
            double clientDirZ,
            int jumpTicksAgo,
            int landingTicksAgo) {
        tryBoostFromKey(player, clientDirX, clientDirZ, jumpTicksAgo, landingTicksAgo, null);
    }

    public static void tryBoostFromKey(
            ServerPlayer player,
            double clientDirX,
            double clientDirZ,
            int jumpTicksAgo,
            int landingTicksAgo,
            ChargeSessionTracker.ChargeBoostContext charge) {
        BoosterEquipment.Equipped equipped = BoosterEquipment.find(player).orElse(null);
        if (equipped == null) {
            return;
        }
        BoosterLeggingsItem boosterItem = equipped.item();
        ItemStack boosterStack = equipped.stack();
        if (BoosterMotionTicker.isBoosting(player)) {
            return;
        }

        var registries = player.registryAccess();
        boolean noCooldown = BoosterUpgradeHelper.hasUpgrade(
                boosterStack, BoosterUpgradeType.NO_COOLDOWN, registries);
        if (!noCooldown && player.getCooldowns().isOnCooldown(boosterItem)) {
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        if (!creative && player.getFoodData().getFoodLevel() < MIN_FOOD_LEVEL) {
            return;
        }

        ServerLevel level = player.serverLevel();
        boolean groundLaunch = player.onGround();
        if (!groundLaunch && !BoosterUpgradeHelper.hasUpgrade(
                boosterStack, BoosterUpgradeType.AIR_DASH, registries)) {
            return;
        }

        double distanceMultiplier = charge == null ? 1.0 : charge.distanceMultiplier();
        boolean overloaded = charge != null && charge.overloaded();

        boolean burrowUpgrade = BoosterUpgradeHelper.hasUpgrade(
                boosterStack, BoosterUpgradeType.BURROW, registries);
        if (burrowUpgrade && isLookingRoughlyDown(player)) {
            boolean burrowed = boosterItem.applyBurrow(level, player, equipped);
            if (!burrowed && !overloaded) {
                return;
            }
            // 过载遁地：即使 0 格失败也在当前位置炸一次；成功则先遁地再炸。
            if (overloaded) {
                OverloadExplosion.detonate(level, player);
            }
            if (!burrowed) {
                return;
            }
            ServerPlayNetworking.send(player, new BoosterFeedbackPayload(false));
            if (!noCooldown) {
                player.getCooldowns().addCooldown(boosterItem, COOLDOWN_TICKS);
            }
            player.swing(InteractionHand.MAIN_HAND, true);
            return;
        }

        Vec3 direction = BoosterUpgradeHelper.hasUpgrade(
                boosterStack, BoosterUpgradeType.VERTICAL_LAUNCH, registries)
                ? VERTICAL_LAUNCH_DIRECTION
                : boosterItem.resolveBoostDirection(player);
        if (direction == null) {
            return;
        }

        boolean hyper = isHyperBoost(player, jumpTicksAgo, landingTicksAgo);
        boolean randomImpulse = BoosterUpgradeHelper.hasUpgrade(
                boosterStack, BoosterUpgradeType.RANDOM_IMPULSE, registries);
        boosterItem.applyBoost(
                level,
                player,
                equipped,
                direction,
                hyper,
                groundLaunch,
                randomImpulse,
                distanceMultiplier,
                overloaded);
        ServerPlayNetworking.send(player, new BoosterFeedbackPayload(hyper));
        if (!noCooldown) {
            player.getCooldowns().addCooldown(boosterItem, COOLDOWN_TICKS);
        }
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private static boolean isLookingRoughlyDown(Player player) {
        return player.getXRot() >= BURROW_LOOK_DOWN_PITCH_DEG;
    }

    private static boolean isHyperBoost(ServerPlayer player, int jumpTicksAgo, int landingTicksAgo) {
        boolean landingWindow = player.onGround() && landingTicksAgo >= 0 && landingTicksAgo <= HYPER_WINDOW_TICKS;
        return landingWindow;
    }

    public static void tickActiveMotions(MinecraftServer server) {
        BoosterMotionTicker.tickServer(server);
    }

    /**
     * 计算推进方向。贴墙也允许推进，撞墙由运动步进挡住（不再因前向探测失败拒发）。
     */
    private Vec3 resolveBoostDirection(Player player) {
        Vec3 direction = player.getViewVector(1.0f);
        if (direction.lengthSqr() < 1.0e-6) {
            return null;
        }
        return direction.normalize();
    }

    private void applyBoost(
            ServerLevel level,
            ServerPlayer player,
            BoosterEquipment.Equipped equipped,
            Vec3 direction,
            boolean hyper,
            boolean groundLaunch,
            boolean randomImpulse,
            double distanceMultiplier,
            boolean overloaded) {
        BoosterBalanceProfile balance = randomImpulse ? withRandomImpulse(currentBalance(level)) : currentBalance(level);
        if (distanceMultiplier != 1.0) {
            balance = new BoosterBalanceProfile(
                    balance.impulse() * distanceMultiplier,
                    balance.thrustPerTick() * distanceMultiplier,
                    balance.thrustTicks());
        }
        Vec3 originEye = player.getEyePosition();
        double eyeOffsetY = player.getEyeY() - player.getY();

        spendBoostResources(level, player, equipped);
        playBoostSound(level, originEye);

        BoosterMotionTicker.start(
                level, player, direction, balance, originEye, eyeOffsetY, hyper, groundLaunch, overloaded);
    }

    private boolean applyBurrow(
            ServerLevel level,
            ServerPlayer player,
            BoosterEquipment.Equipped equipped) {
        Vec3 originEye = player.getEyePosition();
        double eyeOffsetY = player.getEyeY() - player.getY();
        int descended = descendByBreakingBlocks(level, player);
        if (descended <= 0) {
            return false;
        }

        spendBoostResources(level, player, equipped);
        playBoostSound(level, originEye);
        player.setDeltaMovement(0.0, -0.1, 0.0);
        player.resetFallDistance();
        player.hurtMarked = true;
        emitTrailParticles(level, originEye, player.position().add(0.0, eyeOffsetY, 0.0));
        return true;
    }

    private static int descendByBreakingBlocks(ServerLevel level, ServerPlayer player) {
        int descended = 0;
        for (int i = 0; i < BURROW_DEPTH_BLOCKS; i++) {
            AABB targetBox = player.getBoundingBox().move(0.0, -1.0, 0.0);
            if (!canBurrowInto(level, player, targetBox)) {
                break;
            }

            player.teleportTo(player.getX(), player.getY() - 1.0, player.getZ());
            player.resetFallDistance();
            player.hurtMarked = true;
            descended++;
        }
        return descended;
    }

    private static boolean canBurrowInto(ServerLevel level, ServerPlayer player, AABB targetBox) {
        if (!level.getWorldBorder().isWithinBounds(targetBox) || !level.getEntityCollisions(player, targetBox).isEmpty()) {
            return false;
        }

        List<BlockPos> blocksToBreak = collectBreakableBlocks(level, targetBox);
        if (blocksToBreak == null) {
            return false;
        }

        for (BlockPos pos : blocksToBreak) {
            if (!level.destroyBlock(pos, true, player)) {
                return false;
            }
        }
        return !level.getBlockCollisions(player, targetBox).iterator().hasNext();
    }

    private static List<BlockPos> collectBreakableBlocks(ServerLevel level, AABB box) {
        List<BlockPos> blocks = new ArrayList<>();
        int minX = floorInside(box.minX);
        int minY = floorInside(box.minY);
        int minZ = floorInside(box.minZ);
        int maxX = floorInside(box.maxX - BOX_EDGE_EPSILON);
        int maxY = floorInside(box.maxY - BOX_EDGE_EPSILON);
        int maxZ = floorInside(box.maxZ - BOX_EDGE_EPSILON);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    if (state.getDestroySpeed(level, pos) < 0.0f) {
                        return null;
                    }
                    blocks.add(pos);
                }
            }
        }
        return blocks;
    }

    private static int floorInside(double value) {
        return (int) Math.floor(value + BOX_EDGE_EPSILON);
    }

    private static void spendBoostResources(
            ServerLevel level,
            ServerPlayer player,
            BoosterEquipment.Equipped equipped) {
        player.causeFoodExhaustion(MOVEMENT_BOOST_HUNGER_EXHAUSTION);
        if (!player.getAbilities().instabuild) {
            equipped.applyBoostDamage(player, level);
        }
    }

    private static void playBoostSound(ServerLevel level, Vec3 originEye) {
        level.playSound(null, originEye.x, originEye.y, originEye.z,
                SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, BOOST_SOUND_VOLUME, BOOST_SOUND_PITCH);
    }

    private static BoosterBalanceProfile withRandomImpulse(BoosterBalanceProfile balance) {
        double impulse = ThreadLocalRandom.current().nextDouble(RANDOM_IMPULSE_MIN, RANDOM_IMPULSE_MAX);
        return new BoosterBalanceProfile(impulse, balance.thrustPerTick(), balance.thrustTicks());
    }

    /** 世界可见的云雾轨迹；玩家推进与村民推进共用。 */
    public static void emitTrailParticles(ServerLevel level, Vec3 from, Vec3 to) {
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
