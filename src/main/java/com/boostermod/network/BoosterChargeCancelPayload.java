package com.boostermod.network;

import com.boostermod.BoosterMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S：取消蓄力（空包）。不推进、不结算资源。 */
public record BoosterChargeCancelPayload() implements CustomPacketPayload {
    public static final BoosterChargeCancelPayload INSTANCE = new BoosterChargeCancelPayload();

    public static final CustomPacketPayload.Type<BoosterChargeCancelPayload> TYPE =
            new CustomPacketPayload.Type<>(BoosterMod.id("booster_charge_cancel"));

    public static final StreamCodec<ByteBuf, BoosterChargeCancelPayload> CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
