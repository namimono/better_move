package com.boostermod.network;

import com.boostermod.BoosterMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S：开始蓄力（空包）。蓄力时长由服务端时钟权威计算。 */
public record BoosterChargeStartPayload() implements CustomPacketPayload {
    public static final BoosterChargeStartPayload INSTANCE = new BoosterChargeStartPayload();

    public static final CustomPacketPayload.Type<BoosterChargeStartPayload> TYPE =
            new CustomPacketPayload.Type<>(BoosterMod.id("booster_charge_start"));

    public static final StreamCodec<ByteBuf, BoosterChargeStartPayload> CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
