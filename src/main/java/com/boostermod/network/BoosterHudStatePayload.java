package com.boostermod.network;

import com.boostermod.BoosterMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BoosterHudStatePayload(boolean enabled) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BoosterHudStatePayload> TYPE =
            new CustomPacketPayload.Type<>(BoosterMod.id("booster_hud_state"));

    public static final StreamCodec<ByteBuf, BoosterHudStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BoosterHudStatePayload::enabled,
            BoosterHudStatePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
