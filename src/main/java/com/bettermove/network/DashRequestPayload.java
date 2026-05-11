package com.bettermove.network;

import com.bettermove.BetterMoveMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端按下冲刺键后向服务端发送的请求包。
 *
 * <p>携带客户端测量到的<strong>水平移动方向</strong>（未归一化的 x/z 分量，由客户端
 * 物理模拟的 {@code deltaMovement} 取出）。这是唯一可靠的"玩家此刻在朝哪走"——
 * 服务端的 {@code ServerPlayer.getDeltaMovement()} 和 {@code xo/zo} 在 vanilla 处理
 * 玩家移动包（走的是 {@code absMoveTo}）时都会被重置成与当前位置一致，水平分量
 * 永远是 0。</p>
 *
 * <p>距离、冷却、饥饿、腿部装备等关键校验仍以服务端权威状态为准；客户端就算改包
 * 伪造方向，最多让自己"选一个反常方向冲刺"，没有 exploit 空间。玩家没在走
 * （dirX=dirZ=0）时由服务端退化到视线 / 朝向方向。</p>
 */
public record DashRequestPayload(double dirX, double dirZ) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DashRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(BetterMoveMod.id("dash_request"));

    public static final StreamCodec<ByteBuf, DashRequestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, DashRequestPayload::dirX,
            ByteBufCodecs.DOUBLE, DashRequestPayload::dirZ,
            DashRequestPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
