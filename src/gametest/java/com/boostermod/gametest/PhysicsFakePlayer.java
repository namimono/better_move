package com.boostermod.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * 会处理位移与碰撞的 FakePlayer，供推进 / 破壁 GameTest 观察外部世界状态。
 * Fabric 默认 {@code FakePlayer#tick} 为空，无法产生水平碰撞或破壁所需位移。
 */
public final class PhysicsFakePlayer extends VulnerableFakePlayer {
    private static final double AIR_DRAG_XZ = 0.91;
    private static final double AIR_DRAG_Y = 0.98;
    private static final double GRAVITY = 0.08;

    public PhysicsFakePlayer(ServerLevel world, GameProfile profile) {
        super(world, profile);
    }

    @Override
    public void tick() {
        super.tick();

        Vec3 motion = this.getDeltaMovement();
        this.move(MoverType.SELF, motion);

        Vec3 after = this.getDeltaMovement();
        if (this.isNoGravity()) {
            this.setDeltaMovement(after.x * AIR_DRAG_XZ, after.y * AIR_DRAG_Y, after.z * AIR_DRAG_XZ);
        } else {
            double drag = this.onGround() ? 0.6 * AIR_DRAG_XZ : AIR_DRAG_XZ;
            this.setDeltaMovement(after.x * drag, (after.y - GRAVITY) * AIR_DRAG_Y, after.z * drag);
        }
    }
}
