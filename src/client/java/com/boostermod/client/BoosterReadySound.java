package com.boostermod.client;

import com.boostermod.item.BoosterEquipment;
import com.boostermod.item.BoosterLeggingsItem;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

final class BoosterReadySound {
    private static final float READY_SOUND_VOLUME = 0.2f;
    private static final float READY_SOUND_PITCH = 1.8f;
    /** 击杀清 CD 主动播就绪音后，短时间跳过边沿检测，避免叠播。 */
    private static final int SUPPRESS_EDGE_TICKS = 8;

    private static boolean hadBoosterCooldown;
    private static int suppressEdgeTicks;

    private BoosterReadySound() {}

    static void reset() {
        hadBoosterCooldown = false;
        suppressEdgeTicks = 0;
    }

    /**
     * 主动播放冷却就绪音（击杀重置冷却时调用）。
     */
    static void playReady(Player player) {
        if (player == null) {
            return;
        }
        player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, READY_SOUND_VOLUME, READY_SOUND_PITCH);
        hadBoosterCooldown = false;
        suppressEdgeTicks = SUPPRESS_EDGE_TICKS;
    }

    static void tick(LocalPlayer player) {
        BoosterEquipment.Equipped equipped = BoosterEquipment.find(player).orElse(null);
        if (equipped == null) {
            reset();
            return;
        }
        if (!(equipped.item() instanceof BoosterLeggingsItem boosterItem)) {
            reset();
            return;
        }

        boolean hasCooldown = player.getCooldowns().isOnCooldown(boosterItem);
        if (suppressEdgeTicks > 0) {
            suppressEdgeTicks--;
            hadBoosterCooldown = hasCooldown;
            return;
        }
        if (hadBoosterCooldown && !hasCooldown) {
            player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, READY_SOUND_VOLUME, READY_SOUND_PITCH);
        }
        hadBoosterCooldown = hasCooldown;
    }
}
