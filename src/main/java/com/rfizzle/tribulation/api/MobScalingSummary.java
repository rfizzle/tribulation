package com.rfizzle.tribulation.api;

/**
 * Read-only summary of the scaling a mob received from Tribulation at spawn.
 * Obtained via {@link TribulationAPI#getMobScalingSummary}; only mobs that
 * actually went through scaling have one.
 *
 * <p>The factor values are the sums of Tribulation's attribute modifiers as
 * currently attached to the mob, in the attribute's native units. Health and
 * damage use {@code ADD_MULTIPLIED_BASE}, so a factor of {@code 0.5} means
 * +50% of the mob's base value. Boss-scaled mobs report their boss-axis
 * contributions in the same fields.
 *
 * @param tier         the Tribulation tier (0-5) the mob was scaled to at spawn
 * @param bossScaled   true if the mob was scaled with the boss formula rather
 *                     than the normal per-mob formula
 * @param healthFactor bonus max-health as a fraction of base ({@code 0.5} = +50%)
 * @param damageFactor bonus attack damage as a fraction of base ({@code 0.5} = +50%)
 */
@Stable
public record MobScalingSummary(int tier, boolean bossScaled, double healthFactor, double damageFactor) {
}
