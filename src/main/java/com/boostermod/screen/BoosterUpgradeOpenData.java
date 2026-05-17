package com.boostermod.screen;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record BoosterUpgradeOpenData(boolean mainHand) {
    public static final StreamCodec<RegistryFriendlyByteBuf, BoosterUpgradeOpenData> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    BoosterUpgradeOpenData::mainHand,
                    BoosterUpgradeOpenData::new);
}
