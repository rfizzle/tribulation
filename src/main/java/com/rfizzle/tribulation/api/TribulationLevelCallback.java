package com.rfizzle.tribulation.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

/**
 * Callback for when a player's Tribulation level changes.
 * Only fired on the server.
 */
public interface TribulationLevelCallback {
    Event<TribulationLevelCallback> EVENT = EventFactory.createArrayBacked(TribulationLevelCallback.class,
            (listeners) -> (player, oldLevel, newLevel) -> {
                for (TribulationLevelCallback listener : listeners) {
                    listener.onLevelChanged(player, oldLevel, newLevel);
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
