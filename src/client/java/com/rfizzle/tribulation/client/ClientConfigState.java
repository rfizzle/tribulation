package com.rfizzle.tribulation.client;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the config synced from the server, and resolves the config a client
 * surface should read with server-first precedence.
 *
 * <p>Precedence: the synced server config wins when present; otherwise the
 * local file is used. This keeps singleplayer and servers-without-the-mod
 * working on the local file while a dedicated server with custom tuning drives
 * what the client draws (e.g. the Shatter Shard recipe-viewer info panel).
 *
 * <p>The synced copy is cleared on disconnect (via
 * {@link ClientTribulationState#reset()}) so the next singleplayer world falls
 * back to the local file.
 */
public final class ClientConfigState {
    @Nullable
    private static volatile TribulationConfig serverConfig;

    private ClientConfigState() {}

    public static void setServerConfig(@Nullable TribulationConfig config) {
        serverConfig = config;
    }

    /**
     * The config a client surface should read: the synced server config when
     * connected to a server that sent one, otherwise the local file. Never
     * {@code null} — falls back to a fresh default if the local config has not
     * loaded yet.
     */
    public static TribulationConfig effective() {
        TribulationConfig synced = serverConfig;
        if (synced != null) {
            return synced;
        }
        TribulationConfig local = Tribulation.getConfig();
        return local != null ? local : new TribulationConfig();
    }

    public static void clear() {
        serverConfig = null;
    }
}
