package com.bettermove.network;

import com.bettermove.BetterMoveMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端按下冲刺键后向服务端发送的请求包。
 *
 * <p>包体为空——服务端只关心「这名玩家请求触发冲刺」这件事，
 * 玩家身份由网络上下文携带，冲刺方向、距离等参数全部以
 * 服务端权威状态（玩家移动方向、腿部装备等级、饥饿值、冷却）为准，
 * 避免客户端伪造数据。</p>
 */
public record DashRequestPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DashRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(BetterMoveMod.id("dash_request"));

    public static final StreamCodec<ByteBuf, DashRequestPayload> CODEC =
            StreamCodec.unit(new DashRequestPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
