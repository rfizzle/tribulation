package com.rfizzle.tribulation.compat.common;

import com.rfizzle.tribulation.compat.emi.EmiShardRefresh;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Refreshes the loaded recipe viewers after the server's config lands on the
 * client, so the Shatter Shard info panel reflects the server's tuning on a
 * remote dedicated server and updates on a live {@code /tribulation reload}.
 *
 * <p>EMI is force-refreshed through its internal reload (reached by reflection),
 * which re-runs {@code EmiShardPlugin} against the freshly-synced config. JEI and
 * REI expose no safe way to re-drive an info panel at runtime — their panels rely
 * on the sync landing before they build their lists (correct on first join, the
 * common case) and otherwise pick up a live reload on rejoin or a manual resource
 * reload (F3+T). {@code ClientConfigState.effective()} is updated for all three
 * regardless, so each shows server-accurate numbers on the next (re)build.
 */
public final class ShardViewerRefresh {

    private ShardViewerRefresh() {
    }

    public static void refreshViewers() {
        if (FabricLoader.getInstance().isModLoaded("emi")) {
            EmiShardRefresh.reload();
        }
    }
}
