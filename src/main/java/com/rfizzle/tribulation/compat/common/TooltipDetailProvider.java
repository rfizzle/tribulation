package com.rfizzle.tribulation.compat.common;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Bridges the client-only inputs a base item tooltip needs into the shared
 * {@code main} source set, where {@code Item.appendHoverText} lives.
 *
 * <p>{@code splitEnvironmentSourceSets()} keeps client classes off the
 * {@code main} compile classpath, so the item classes cannot call
 * {@code Screen.hasShiftDown()} or {@code ClientConfigState.effective()}
 * directly. The client entrypoint wires those in at init; until then the
 * defaults keep every accessor null-safe (tooltips only ever render
 * client-side, so in practice the suppliers are always set before use).
 */
public final class TooltipDetailProvider {

    private static volatile BooleanSupplier shiftHeld = () -> false;
    private static volatile Supplier<TribulationConfig> effectiveConfig = () -> {
        TribulationConfig cfg = Tribulation.getConfig();
        return cfg != null ? cfg : new TribulationConfig();
    };

    private TooltipDetailProvider() {}

    public static void setShiftHeld(BooleanSupplier supplier) {
        shiftHeld = supplier;
    }

    public static void setEffectiveConfig(Supplier<TribulationConfig> supplier) {
        effectiveConfig = supplier;
    }

    /** Whether the detail-expand modifier (Shift) is currently held. */
    public static boolean isShiftHeld() {
        return shiftHeld.getAsBoolean();
    }

    /**
     * The config a client tooltip should read: the synced server config when
     * connected to a server that sent one, otherwise the local file. Never
     * {@code null}.
     */
    public static TribulationConfig effectiveConfig() {
        return effectiveConfig.get();
    }
}
