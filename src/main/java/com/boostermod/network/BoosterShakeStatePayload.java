package com.boostermod.network;

import com.boostermod.BoosterMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BoosterShakeStatePayload(boolean enabled) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BoosterShakeStatePayload> TYPE =
            new CustomPacketPayload.Type<>(BoosterMod.id("booster_shake_state"));

    public static final StreamCodec<ByteBuf, BoosterShakeStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BoosterShakeStatePayload::enabled,
            BoosterShakeStatePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
