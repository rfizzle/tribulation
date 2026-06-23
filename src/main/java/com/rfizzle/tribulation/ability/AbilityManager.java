package com.rfizzle.tribulation.ability;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Applies tier-based abilities to scaled mobs after the {@link
 * com.rfizzle.tribulation.scaling.ScalingEngine} has set stat modifiers. Each
 * ability is declared once in {@link MobAbilities#REGISTRY} as a {@link
 * MobAbility} and expressed as a namespaced attribute modifier, an
 * infinite-duration status effect, a vanilla setter, an equipment change, a
 * scoreboard tag (for mixin-driven on-hit abilities), or a goal injection — all
 * of which persist in the mob's own NBT so abilities survive save/load without
 * extra tracking.
 *
 * <p>The same registry backs the client tier detail panel, so what the panel
 * lists can never diverge from what is actually applied. Every ability checks
 * its corresponding toggle in {@link TribulationConfig.Abilities} before
 * applying; server admins can disable any individual ability without affecting
 * the rest.
 */
public final class AbilityManager {
    static final double ZOMBIE_REINFORCEMENT_BONUS = 0.10;
    static final double SPRINT_SPEED_BONUS = 0.15;
    static final double HOGLIN_KB_BONUS = 0.5;
    static final double ZOMBIFIED_PIGLIN_AGGRO_BONUS = 0.5;
    static final int CREEPER_SHORT_FUSE_TICKS = 15;
    static final float CREEPER_CHARGED_CHANCE = 0.25f;
    static final double SPIDER_LEAP_BONUS = 0.5;

    /**
     * Scoreboard tags read by the narrow ability mixins and the probe-tooltip
     * data collector. The tag — not an attribute modifier — is the canonical
     * "this mob has ability X" signal for abilities expressed through vanilla
     * mechanics that no attribute can capture (arrow effects, potion type,
     * roar radius, door breaking, beam charge, infested-block summons).
     */
    public static final String TAG_SLOWNESS_ARROWS = "tribulation_slow2";
    public static final String TAG_POISON_ARROWS = "tribulation_poison2";
    public static final String TAG_LINGERING_POTIONS = "tribulation_lingering_potions";
    public static final String TAG_AGGRESSIVE_HEALING = "tribulation_aggro_heal";
    public static final String TAG_DOOR_BREAKING = "tribulation_door_break";
    public static final String TAG_GUARDIAN_BEAM = "tribulation_guardian_beam";
    public static final String TAG_RAVAGER_ROAR = "tribulation_ravager_roar";
    public static final String TAG_CALL_SLEEPERS = "tribulation_call_sleepers";

    private AbilityManager() {}

    public static ResourceLocation abilityId(String name) {
        return Tribulation.id("ability_" + name);
    }

    /**
     * Apply tier-appropriate abilities to the mob by iterating {@link
     * MobAbilities#REGISTRY}: every entry whose mob key matches, whose unlock
     * tier is reached, and whose toggle is enabled has its action applied. Tier
     * 0, unknown mob keys, and disabled toggles are no-ops. A plain loop (not a
     * stream) keeps this allocation-free — it runs on every hostile spawn.
     * Exceptions are caught and logged so one bad entity never aborts the spawn
     * handler.
     */
    public static void applyAbilities(Mob mob, int tier, String mobKey, TribulationConfig cfg) {
        if (mob == null || cfg == null || mobKey == null || tier <= 0) return;
        try {
            for (MobAbility ability : MobAbilities.REGISTRY) {
                if (ability.unlockTier() <= tier
                        && ability.mobKey().equals(mobKey)
                        && ability.enabled().test(cfg.abilities)) {
                    ability.apply().accept(mob, cfg);
                }
            }
        } catch (Exception e) {
            Tribulation.LOGGER.warn("Failed applying abilities to {} tier {}", mobKey, tier, e);
        }
    }

    // ---- Helpers shared by the registry's apply actions ----

    static void enchantHeldCrossbow(Mob mob, ResourceKey<Enchantment> enchantment, int level) {
        ItemStack mainHand = mob.getMainHandItem();
        if (!mainHand.is(Items.CROSSBOW)) return;
        HolderLookup.RegistryLookup<Enchantment> lookup =
                mob.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        lookup.get(enchantment).ifPresent(holder -> mainHand.enchant(holder, level));
    }

    static void addAttributeModifier(Mob mob, Holder<Attribute> attr, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addPermanentModifier(new AttributeModifier(id, amount, op));
    }

    static void applyInfiniteEffect(Mob mob, Holder<MobEffect> effect, int amplifier) {
        mob.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, amplifier, false, false));
    }
}
