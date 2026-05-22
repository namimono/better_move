package com.boostermod.client;

import com.boostermod.item.BoosterEquipment;
import com.boostermod.item.BoosterLeggingsItem;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;

final class BoosterReadySound {
    private static final float READY_SOUND_VOLUME = 0.2f;
    private static final float READY_SOUND_PITCH = 1.8f;

    private static boolean hadBoosterCooldown;

    private BoosterReadySound() {}

    static void reset() {
        hadBoosterCooldown = false;
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
        if (hadBoosterCooldown && !hasCooldown) {
            player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, READY_SOUND_VOLUME, READY_SOUND_PITCH);
        }
        hadBoosterCooldown = hasCooldown;
    }
}
