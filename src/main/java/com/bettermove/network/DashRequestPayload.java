package com.bettermove.network;

import com.bettermove.BetterMoveMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端按下冲刺键后向服务端发送的请求包。
 *
 * <p>携带客户端按键输入推导出的<strong>水平意图方向</strong>（未归一化的 x/z 分量）。
 * 这样空中残留惯性与当前按键相反时，冲刺也会跟随玩家此刻的操作意图。服务端拿不到这份
 * 即时输入，只能收到同步后的位置结果，所以需要客户端在按键瞬间把方向一并送上来。</p>
 *
 * <p>距离、冷却、饥饿、腿部装备等关键校验仍以服务端权威状态为准；客户端就算改包
 * 伪造方向，最多让自己"选一个反常方向冲刺"，没有 exploit 空间。玩家当前没给移动输入
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
