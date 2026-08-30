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
 * can be far larger, so the string uses an explicit cap rather than the
 * 32&nbsp;KiB default of {@code ByteBufCodecs.STRING_UTF8}.
 *
 * <p>The payload carries the blob, not a config: {@code decode()} only reads a
 * bounded string, and the JSON is parsed and clamped by the handler on the
 * client thread, where a malformed or hostile blob can be handled instead of
 * disconnecting the player.
 */
public record ConfigSyncPayload(String json) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ConfigSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(Tribulation.id("config_sync"));

    /**
     * The hard wire limit. Note the unit: {@code stringUtf8(n)} bounds
     * <em>characters</em>, so the wire allowance is up to {@code 3n} bytes —
     * 768&nbsp;KiB here, under vanilla's 1&nbsp;MiB S2C custom-payload ceiling,
     * which would otherwise kill the connection first with an error naming
     * netty rather than this payload. That is ~28× the default compact config
     * and well past the suite's 4× rule of thumb, so a config that fills it is
     * not a Tribulation config. (The previous 4&nbsp;MiB "cap" sat above the
     * vanilla ceiling and so bounded nothing.)
     */
    public static final int MAX_JSON_CHARS = 256 * 1024;

    public static final StreamCodec<FriendlyByteBuf, ConfigSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_JSON_CHARS), ConfigSyncPayload::json,
                    ConfigSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
