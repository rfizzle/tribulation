package com.rfizzle.tribulation.network;

import com.rfizzle.tribulation.Tribulation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C flag telling clients whether a Blood Moon is currently active in the
 * Overworld, driving the red sky/fog/moon tint. Sent on join and on every
 * event start/end so dedicated-server clients stay in step.
 */
public record BloodMoonPayload(boolean active) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BloodMoonPayload> TYPE =
            new CustomPacketPayload.Type<>(Tribulation.id("blood_moon"));

    public static final StreamCodec<FriendlyByteBuf, BloodMoonPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, BloodMoonPayload::active,
                    BloodMoonPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
