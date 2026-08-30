package com.rfizzle.tribulation.api;

import com.rfizzle.tribulation.Tribulation;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

/**
 * Callback for when a player's Tribulation level changes.
 * Only fired on the server — on playtime progression, death relief,
 * Shatter Shard use, Ascendant Shard use, and {@code /tribulation set} /
 * {@code /tribulation reset}.
 *
 * <p>A listener that throws is caught, logged, and skipped; it can never break
 * level progression or the listeners registered after it. The isolation lives
 * in the invoker, so fire sites stay bare.
 */
@Stable
public interface TribulationLevelCallback {

    /** One-shot gate so a listener that throws on every level change logs its stack trace once. */
    AtomicBoolean LISTENER_FAILURE_LOGGED = new AtomicBoolean(false);

    Event<TribulationLevelCallback> EVENT = EventFactory.createArrayBacked(TribulationLevelCallback.class,
            listeners -> (player, oldLevel, newLevel) -> {
                for (TribulationLevelCallback listener : listeners) {
                    try {
                        listener.onLevelChanged(player, oldLevel, newLevel);
                    } catch (VirtualMachineError e) {
                        throw e; // OOME/SOE: the JVM is gone, not the guest
                    } catch (Throwable t) {
                        // Throwable, not Exception: a listener compiled against an older
                        // signature throws Error (AbstractMethodError, NoClassDefFoundError),
                        // which an Exception catch would let escape and kill the server tick.
                        if (LISTENER_FAILURE_LOGGED.compareAndSet(false, true)) {
                            Tribulation.LOGGER.warn("A TribulationLevelCallback listener {} threw; skipping",
                                    listener.getClass().getName(), t);
                        }
                    }
                }
            });

    /**
     * Called when a player's level changes.
     *
     * @param player   the player whose level changed
     * @param oldLevel the level before the change
     * @param newLevel the level after the change
     */
    void onLevelChanged(ServerPlayer player, int oldLevel, int newLevel);
}
