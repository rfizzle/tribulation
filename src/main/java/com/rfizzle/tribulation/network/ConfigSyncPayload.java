package com.rfizzle.tribulation.network;

import com.rfizzle.tribulation.Tribulation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C snapshot of the server's active config, serialized to the same JSON form
 * written to disk. Sent on join and re-broadcast after a server-side config
 * reload so client-side surfaces (recipe-viewer info panels, tooltips) show the
 * server's tuning rather than the client's local file.
 *
 * <p>Only client-consumed fields are read from the resulting config, but the
 * whole object travels — it is sent once per join, and carrying everything keeps
 * future client readers working without extending the codec. The default config
 * is ~9&nbsp;KB compact, and a heavily customized one (large scaling/offset maps)
 * can be far larger, so the string uses an explicit high cap rather than the
 * 32&nbsp;KiB default of {@code ByteBufCodecs.STRING_UTF8}.
 */
public record ConfigSyncPayload(String json) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ConfigSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(Tribulation.id("config_sync"));

    /** 4 MiB ceiling — far above any realistic serialized config, still bounded. */
    private static final int MAX_JSON_BYTES = 4 * 1024 * 1024;

    public static final StreamCodec<FriendlyByteBuf, ConfigSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_JSON_BYTES), ConfigSyncPayload::json,
                    ConfigSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
