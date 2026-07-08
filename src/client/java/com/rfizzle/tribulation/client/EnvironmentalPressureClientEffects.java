package com.rfizzle.tribulation.client;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Ambient-light math for Oppressive Nights. The server is authoritative for
 * <em>whether</em> pressure applies — the synced darkness strength already
 * folds in the master toggle and the local player's tier — so this class owns
 * only the presentation rules: night-only, daylight-cycle dimensions only, a
 * hard accessibility ceiling on whatever the server sent, and the
 * {@code oppressiveNights.clientEnabled} opt-out read from the client's local
 * config. The curve is kept as pure static functions so it is unit-testable.
 */
public final class EnvironmentalPressureClientEffects {

    /**
     * Hard ceiling on the extra lightmap darkness regardless of what the
     * server sent, mirroring the server-side clamp on
     * {@code oppressiveNights.maxDarkness}. Keeps a misconfigured (or
     * malicious) server from blacking out the client's screen.
     */
    static final float MAX_DARKNESS = 0.6f;

    private EnvironmentalPressureClientEffects() {}

    /**
     * Extra lightmap darkness to apply right now, {@code 0} when inactive.
     * Called once per lightmap rebuild (at most once per tick) from
     * {@code OppressiveNightLightMixin}.
     */
    public static float nightDarkness(ClientLevel level, float partialTick) {
        float synced = ClientTribulationState.getOppressiveNightDarkness();
        // !(synced > 0f) is true for NaN as well as for non-positive values, so
        // a non-finite intensity reads as "off" instead of slipping the gate.
        if (!(synced > 0f) || level == null) return 0f;
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.environmentalPressure.oppressiveNights.clientEnabled) return 0f;
        DimensionType dimension = level.dimensionType();
        // Night only exists where time advances and the sky is visible — the
        // Nether/End (fixed time) and modded skyless dimensions stay vanilla.
        if (dimension.hasFixedTime() || !dimension.hasSkyLight()) return 0f;
        return computeDarkness(synced, level.getTimeOfDay(partialTick));
    }

    /** Bounded darkness scaled by how deep into the night we are. */
    static float computeDarkness(float maxDarkness, float timeOfDay) {
        // A non-finite server value is rejected outright: Math.min(NaN, ceiling)
        // is NaN, which the MAX_DARKNESS clamp cannot tame, so no darkness.
        if (!Float.isFinite(maxDarkness)) return 0f;
        return Math.min(maxDarkness, MAX_DARKNESS) * nightFactor(timeOfDay);
    }

    /**
     * 0 through the day, 1 at midnight, with a smooth cosine ramp through
     * dusk and dawn — the inverse of the pure-time part of vanilla's sky
     * brightness curve ({@code Level#getSkyDarken}), deliberately without the
     * weather terms so a thunderstorm at noon never triggers night pressure.
     */
    static float nightFactor(float timeOfDay) {
        float dayBrightness = Mth.clamp(
                Mth.cos(timeOfDay * (float) (Math.PI * 2)) * 2.0f + 0.5f, 0.0f, 1.0f);
        return 1.0f - dayBrightness;
    }
}
