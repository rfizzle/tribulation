package com.rfizzle.tribulation.network;

import com.rfizzle.tribulation.Tribulation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C per-player oppressive-nights state: the ambient darkness strength this
 * player's client should render at night, {@code 0} when the effect does not
 * apply (feature off, or the player is below the tier threshold). The value
 * already folds in the server's master toggle and tier gate; the client
 * bounds it and owns the night-only/dimension/opt-out presentation rules.
 * Sent on join and whenever the value changes (piggybacked on level syncs).
 */
public record EnvironmentalPressurePayload(float nightDarkness) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EnvironmentalPressurePayload> TYPE =
            new CustomPacketPayload.Type<>(Tribulation.id("environmental_pressure"));

    public static final StreamCodec<FriendlyByteBuf, EnvironmentalPressurePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, EnvironmentalPressurePayload::nightDarkness,
                    EnvironmentalPressurePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
