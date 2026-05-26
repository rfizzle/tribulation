package com.rfizzle.tribulation.network;

import com.rfizzle.tribulation.Tribulation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TribulationLevelPayload(int level) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TribulationLevelPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tribulation.MOD_ID, "level_sync"));

    public static final StreamCodec<FriendlyByteBuf, TribulationLevelPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, TribulationLevelPayload::level,
                    TribulationLevelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
