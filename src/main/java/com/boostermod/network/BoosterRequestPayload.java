package com.boostermod.network;

import com.boostermod.BoosterMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BoosterRequestPayload(double dirX, double dirZ, int jumpTicksAgo, int landingTicksAgo)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BoosterRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(BoosterMod.id("booster_request"));

    public static final StreamCodec<ByteBuf, BoosterRequestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, BoosterRequestPayload::dirX,
            ByteBufCodecs.DOUBLE, BoosterRequestPayload::dirZ,
            ByteBufCodecs.VAR_INT, BoosterRequestPayload::jumpTicksAgo,
            ByteBufCodecs.VAR_INT, BoosterRequestPayload::landingTicksAgo,
            BoosterRequestPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
