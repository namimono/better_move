package com.boostermod.network;

import com.boostermod.BoosterMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 推进破击命中/击杀反馈（与推进启动 {@link BoosterFeedbackPayload} 分离）。 */
public record BoosterStrikeFeedbackPayload(boolean kill) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BoosterStrikeFeedbackPayload> TYPE =
            new CustomPacketPayload.Type<>(BoosterMod.id("boost_strike_feedback"));

    public static final StreamCodec<ByteBuf, BoosterStrikeFeedbackPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BoosterStrikeFeedbackPayload::kill,
            BoosterStrikeFeedbackPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
