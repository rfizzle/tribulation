package com.rfizzle.tribulation.ability;

import com.rfizzle.tribulation.config.TribulationConfig;
import net.minecraft.world.entity.Mob;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * One declarative tier ability: the single source of truth shared by the
 * server-side {@link AbilityManager} (which applies it) and the client tier
 * detail panel (which displays it). Adding, removing, or re-tiering an ability
 * is a single edit to the {@link MobAbilities#REGISTRY} entry — the apply logic
 * and the panel listing can never drift.
 *
 * @param mobKey     the Tribulation mob key this ability belongs to (a value
 *                   from {@link TribulationConfig#MOB_KEYS}). Distinct mob keys
 *                   may share one toggle — {@code cave_spider} reuses
 *                   {@code spiderWebPlacing}, {@code endermite} reuses
 *                   {@code silverfishCallSleepers} — so each is its own entry.
 * @param abilityKey the snake_case name of the gating {@link TribulationConfig.Abilities}
 *                   field. The panel renders the ability label from
 *                   {@code config.tribulation.abilities.<abilityKey>}; these
 *                   reuse the existing config translation keys. This is a
 *                   separate naming scheme from the attribute-modifier ids
 *                   produced by {@link AbilityManager#abilityId(String)}.
 * @param unlockTier the tier at which the ability becomes active (1..5).
 * @param enabled    predicate over the ability toggles deciding whether this
 *                   ability is switched on in config.
 * @param apply      the server-side action applied to a scaled mob; never run
 *                   client-side, so the panel only ever reads the metadata above.
 */
public record MobAbility(
        String mobKey,
        String abilityKey,
        int unlockTier,
        Predicate<TribulationConfig.Abilities> enabled,
        BiConsumer<Mob, TribulationConfig> apply
) {
}
