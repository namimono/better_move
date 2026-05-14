package com.boostermod.network;

import com.boostermod.BoosterMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端在推进期间持续上报的"移动键修正输入"。
 *
 * <p>在 1.21.1 上服务端只能从 {@code ServerboundPlayerInputPacket} 读到骑乘载具时的 xxa/zza；
 * 步行/飞行场景下服务端对玩家的 WASD 状态一无所知，所以推进期间需要 mod 自己把这两个分量
 * 同步过去，供 {@code BoosterMotionTicker} 在每 tick 给推力叠加方向微调。</p>
 *
 * @param strafe  左右分量，约定 A=+1、D=-1，与原版 {@code Input#leftImpulse} 对齐
 * @param forward 前后分量，约定 W=+1、S=-1，与原版 {@code Input#forwardImpulse} 对齐
 */
public record BoosterSteerPayload(float strafe, float forward) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BoosterSteerPayload> TYPE =
            new CustomPacketPayload.Type<>(BoosterMod.id("booster_steer"));

    public static final StreamCodec<ByteBuf, BoosterSteerPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, BoosterSteerPayload::strafe,
            ByteBufCodecs.FLOAT, BoosterSteerPayload::forward,
            BoosterSteerPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
