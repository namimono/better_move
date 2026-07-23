package com.boostermod.network;

import com.boostermod.BoosterMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 推进破击叠层 HUD 状态（与命中反馈 {@link BoosterStrikeFeedbackPayload} 分离）。
 *
 * @param stackAmount 当前叠层攻击加成总量
 * @param maxStack 当前品质叠层上限（0 表示无数据/清空）
 * @param remainingTicks 叠层剩余 tick（到期后整段清零）
 */
public record BoosterStrikeStackPayload(float stackAmount, float maxStack, int remainingTicks)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BoosterStrikeStackPayload> TYPE =
            new CustomPacketPayload.Type<>(BoosterMod.id("boost_strike_stack"));

    public static final StreamCodec<ByteBuf, BoosterStrikeStackPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, BoosterStrikeStackPayload::stackAmount,
            ByteBufCodecs.FLOAT, BoosterStrikeStackPayload::maxStack,
            ByteBufCodecs.VAR_INT, BoosterStrikeStackPayload::remainingTicks,
            BoosterStrikeStackPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
