package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import net.minecraft.server.level.ServerPlayer;

public final class TotemPenaltyHandler {

    private TotemPenaltyHandler() {}

    public static void onTotemUsed(ServerPlayer player) {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null) return;

        if (cfg.deathRelief.enabled && cfg.totems.countsAsDeathRelief) {
            DeathReliefHandler.applyPenalty(player);
        }

        if (cfg.hardcoreHearts.enabled && !cfg.totems.protectsHearts) {
            HardcoreHeartsHandler.applyPenalty(player);
        }
    }
}
