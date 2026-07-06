package com.rfizzle.tribulation.advancement;

import com.rfizzle.tribulation.Tribulation;
import net.minecraft.advancements.CriteriaTriggers;

/**
 * Registry for Tribulation's custom advancement criterion triggers. Each
 * trigger is registered under a {@code tribulation:*} id and fired from the
 * handler that owns the matching state change. {@link #register()} must run in
 * {@code onInitialize} before any advancement referencing these triggers is
 * loaded.
 */
public final class TribulationCriteria {
    public static final TierReachedCriterion TIER_REACHED = new TierReachedCriterion();
    public static final SimplePlayerTrigger SOULBOUND_SURVIVED = new SimplePlayerTrigger();
    public static final SimplePlayerTrigger SHATTER_SHARD_USED = new SimplePlayerTrigger();
    public static final SimplePlayerTrigger ASCENDANT_SHARD_USED = new SimplePlayerTrigger();
    public static final SimplePlayerTrigger HEART_FRAGMENT_USED = new SimplePlayerTrigger();
    public static final SimplePlayerTrigger TIER_FIVE_MOB_KILLED = new SimplePlayerTrigger();

    private TribulationCriteria() {}

    public static void register() {
        CriteriaTriggers.register(Tribulation.id("tier_reached").toString(), TIER_REACHED);
        CriteriaTriggers.register(Tribulation.id("soulbound_survived").toString(), SOULBOUND_SURVIVED);
        CriteriaTriggers.register(Tribulation.id("shatter_shard_used").toString(), SHATTER_SHARD_USED);
        CriteriaTriggers.register(Tribulation.id("ascendant_shard_used").toString(), ASCENDANT_SHARD_USED);
        CriteriaTriggers.register(Tribulation.id("heart_fragment_used").toString(), HEART_FRAGMENT_USED);
        CriteriaTriggers.register(Tribulation.id("tier_five_mob_killed").toString(), TIER_FIVE_MOB_KILLED);
    }
}
