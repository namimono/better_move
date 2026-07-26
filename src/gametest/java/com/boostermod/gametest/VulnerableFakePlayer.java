package com.boostermod.gametest;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * GameTest 用可受伤 FakePlayer：原版 Fabric FakePlayer 对一切伤害无敌。
 */
public final class VulnerableFakePlayer extends FakePlayer {
    public VulnerableFakePlayer(ServerLevel world, GameProfile profile) {
        super(world, profile);
        this.getAbilities().invulnerable = false;
        this.invulnerableTime = 0;
        clearSpawnInvulnerability(this);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        return false;
    }

    @Override
    public void tick() {
        if (this.invulnerableTime > 0) {
            this.invulnerableTime--;
        }
    }

    private static void clearSpawnInvulnerability(ServerPlayer player) {
        try {
            Field field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            field.setAccessible(true);
            field.setInt(player, 0);
        } catch (ReflectiveOperationException ignored) {
            // 映射名变化时忽略；测试仍会清 invulnerableTime。
        }
    }
}
