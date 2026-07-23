package com.boostermod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

/** 推进破击命中/击杀的客户端表现（与推进启动反馈分离）。 */
public final class BoosterStrikeFeedbackEffects {
    private static final int HIT_DURATION_TICKS = 8;
    private static final int KILL_DURATION_TICKS = 14;

    private static int ticksLeft;
    private static int durationTicks;
    private static boolean kill;

    private BoosterStrikeFeedbackEffects() {}

    public static void trigger(boolean killStrike) {
        kill = killStrike;
        durationTicks = killStrike ? KILL_DURATION_TICKS : HIT_DURATION_TICKS;
        ticksLeft = durationTicks;

        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null) {
            return;
        }

        if (killStrike) {
            playKillSounds(player);
            // 击杀重置推进冷却：立刻给「冷却就绪」反馈，不依赖边沿检测时序。
            BoosterReadySound.playReady(player);
        } else {
            playHitSounds(player);
        }

        if (client.level != null) {
            double x = player.getX();
            double y = player.getY(0.6);
            double z = player.getZ();
            int count = killStrike ? 22 : 12;
            for (int i = 0; i < count; i++) {
                client.level.addParticle(
                        killStrike ? ParticleTypes.CRIT : ParticleTypes.ENCHANTED_HIT,
                        x,
                        y,
                        z,
                        (player.getRandom().nextDouble() - 0.5) * (killStrike ? 0.7 : 0.45),
                        player.getRandom().nextDouble() * (killStrike ? 0.4 : 0.28),
                        (player.getRandom().nextDouble() - 0.5) * (killStrike ? 0.7 : 0.45));
            }
            if (killStrike) {
                for (int i = 0; i < 8; i++) {
                    client.level.addParticle(
                            ParticleTypes.FLASH,
                            x + (player.getRandom().nextDouble() - 0.5) * 0.6,
                            y + player.getRandom().nextDouble() * 0.5,
                            z + (player.getRandom().nextDouble() - 0.5) * 0.6,
                            0.0,
                            0.0,
                            0.0);
                }
            }
        }
    }

    /** 命中：多层冲击感，压过原版普攻音。 */
    private static void playHitSounds(Player player) {
        player.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 1.15f, 0.92f);
        player.playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.95f, 1.05f);
        player.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.75f, 0.85f);
        player.playSound(SoundEvents.TRIDENT_HIT, 0.65f, 1.25f);
        player.playSound(SoundEvents.BREEZE_SHOOT, 0.35f, 1.55f);
    }

    /** 击杀：更重、更炸、更有回报感。 */
    private static void playKillSounds(Player player) {
        player.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 1.25f, 0.72f);
        player.playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.1f, 0.88f);
        player.playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.55f, 1.15f);
        player.playSound(SoundEvents.FIREWORK_ROCKET_BLAST, 0.7f, 0.95f);
        player.playSound(SoundEvents.TRIDENT_THUNDER.value(), 0.45f, 1.4f);
        player.playSound(SoundEvents.PLAYER_LEVELUP, 0.4f, 1.55f);
        player.playSound(SoundEvents.BREEZE_SHOOT, 0.5f, 1.2f);
    }

    public static void tick() {
        if (ticksLeft > 0) {
            ticksLeft--;
        }
    }

    public static boolean isActive() {
        return ticksLeft > 0;
    }

    public static boolean isKill() {
        return kill;
    }

    public static float pulse(float tickDelta) {
        if (ticksLeft <= 0 || durationTicks <= 0) {
            return 0.0f;
        }
        float age = durationTicks - ticksLeft + tickDelta;
        float progress = Math.min(1.0f, Math.max(0.0f, age / durationTicks));
        return (float) Math.pow(1.0f - progress, kill ? 1.2f : 1.45f);
    }
}
