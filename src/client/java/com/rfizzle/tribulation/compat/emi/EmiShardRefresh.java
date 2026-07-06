package com.rfizzle.tribulation.compat.emi;

import com.rfizzle.tribulation.Tribulation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Best-effort EMI recipe reload: asks EMI to re-run its plugins so
 * {@code EmiShardPlugin} re-reads the freshly-synced config through
 * {@code ClientConfigState.effective()} on a remote dedicated server (where the
 * config arrives over the network after EMI first registers) and after a live
 * {@code /tribulation reload}.
 *
 * <p>EMI's only reload entry point is the internal
 * {@code dev.emi.emi.runtime.EmiReloadManager}, which is absent from the
 * {@code :api} artifact this mod compiles against — so it is reached by
 * reflection. The call is wrapped and latches off on the first failure (a future
 * EMI version that moved or renamed the symbol), after which the Shatter Shard
 * info panel simply refreshes on rejoin like JEI/REI. Reflection means this class
 * carries no EMI import, so it is safe to load even when EMI is absent — though
 * the caller still gates on {@code isModLoaded}.
 */
public final class EmiShardRefresh {

    private static volatile boolean unavailable = false;

    private EmiShardRefresh() {
    }

    public static void reload() {
        if (unavailable) {
            return;
        }
        try {
            Class<?> reloadManager = Class.forName("dev.emi.emi.runtime.EmiReloadManager");
            MethodHandle reloadRecipes = MethodHandles.publicLookup()
                    .findStatic(reloadManager, "reloadRecipes", MethodType.methodType(void.class));
            reloadRecipes.invokeExact();
        } catch (Throwable t) {
            unavailable = true;
            Tribulation.LOGGER.warn("EMI shard-info reload unavailable; its info panel will refresh on rejoin", t);
        }
    }
}
