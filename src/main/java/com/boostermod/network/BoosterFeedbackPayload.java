package com.boostermod.network;

import com.boostermod.BoosterMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BoosterFeedbackPayload(boolean hyper) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BoosterFeedbackPayload> TYPE =
            new CustomPacketPayload.Type<>(BoosterMod.id("booster_feedback"));

    public static final StreamCodec<ByteBuf, BoosterFeedbackPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BoosterFeedbackPayload::hyper,
            BoosterFeedbackPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
