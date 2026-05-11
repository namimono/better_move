package com.bettermove.item;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 在多个服务端 tick 内把玩家从起点插值到终点，形成「冲刺」观感而非单帧瞬移。
 */
public final class DashMotionTicker {
    /** 插值分段数；越大越平滑，但位移总时长越长。 */
    public static final int ANIMATION_TICKS = 8;

    private static final Map<UUID, ActiveDash> ACTIVE = new ConcurrentHashMap<>();

    private DashMotionTicker() {}

    public static boolean isDashing(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static void start(
            ServerLevel level,
            ServerPlayer player,
            Vec3 startFeet,
            Vec3 endFeet,
            Vec3 originEye,
            double eyeOffsetY) {
        ACTIVE.put(
                player.getUUID(),
                new ActiveDash(level, startFeet, endFeet, originEye, eyeOffsetY));
    }

    public static void tickServer(MinecraftServer server) {
        Iterator<Map.Entry<UUID, ActiveDash>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ActiveDash> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            if (e.getValue().step(player)) {
                it.remove();
            }
        }
    }

    private static final class ActiveDash {
        private final ServerLevel level;
        private final Vec3 startFeet;
        private final Vec3 endFeet;
        private final Vec3 originEye;
        private final double eyeOffsetY;
        private int tick;

        private ActiveDash(
                ServerLevel level,
                Vec3 startFeet,
                Vec3 endFeet,
                Vec3 originEye,
                double eyeOffsetY) {
            this.level = level;
            this.startFeet = startFeet;
            this.endFeet = endFeet;
            this.originEye = originEye;
            this.eyeOffsetY = eyeOffsetY;
        }

        /**
         * @return {@code true} 表示动画已结束，应从表中移除。
         */
        private boolean step(ServerPlayer player) {
            tick++;
            if (tick >= ANIMATION_TICKS) {
                player.teleportTo(endFeet.x, endFeet.y, endFeet.z);
                player.resetFallDistance();
                Vec3 targetEye = endFeet.add(0.0, eyeOffsetY, 0.0);
                DashToolItem.emitTrailParticles(level, originEye, targetEye);
                return true;
            }
            double t = (double) tick / ANIMATION_TICKS;
            Vec3 nextFeet = startFeet.lerp(endFeet, t);
            AABB box = player.getBoundingBox();
            Vec3 cur = player.position();
            Vec3 move = nextFeet.subtract(cur);
            if (!level.noCollision(player, box.move(move.x, move.y, move.z))) {
                return true;
            }
            player.teleportTo(nextFeet.x, nextFeet.y, nextFeet.z);
            player.resetFallDistance();
            return false;
        }
    }
}
