package com.rfizzle.tribulation.ability;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.mixin.CreeperAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Applies tier-based abilities to scaled mobs after the {@link
 * com.rfizzle.tribulation.scaling.ScalingEngine} has set stat modifiers. Each
 * ability is expressed as a namespaced attribute modifier, an infinite-duration
 * status effect, a vanilla setter, an equipment change, a scoreboard tag
 * (for mixin-driven on-hit abilities), or a goal injection — all of which
 * persist in the mob's own NBT so abilities survive save/load without extra
 * tracking.
 *
 * <p>Every ability checks its corresponding toggle in
 * {@link TribulationConfig.Abilities} before applying. Server admins can
 * disable any individual ability without affecting the rest.
 */
public final class AbilityManager {
    private static final double ZOMBIE_REINFORCEMENT_BONUS = 0.10;
    private static final double SPRINT_SPEED_BONUS = 0.15;
    private static final double HOGLIN_KB_BONUS = 0.5;
    private static final double ZOMBIFIED_PIGLIN_AGGRO_BONUS = 0.5;
    private static final int CREEPER_SHORT_FUSE_TICKS = 15;
    private static final float CREEPER_CHARGED_CHANCE = 0.25f;
    private static final double SPIDER_LEAP_BONUS = 0.5;

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
        return ResourceLocation.fromNamespaceAndPath(Tribulation.MOD_ID, "ability_" + name);
    }

    /**
     * Apply tier-appropriate abilities to the mob. Tier 0 and unknown mob
     * keys are no-ops. Exceptions are caught and logged so one bad entity
     * never aborts the spawn handler.
     */
    public static void applyAbilities(Mob mob, int tier, String mobKey, TribulationConfig cfg) {
        if (mob == null || cfg == null || mobKey == null || tier <= 0) return;
        try {
            switch (mobKey) {
                case "zombie" -> applyZombieAbilities(mob, tier, cfg);
                case "skeleton" -> applySkeletonAbilities(mob, tier, cfg);
                case "creeper" -> applyCreeperAbilities(mob, tier, cfg);
                case "spider" -> applySpiderAbilities(mob, tier, cfg);
                case "cave_spider" -> applyCaveSpiderAbilities(mob, tier, cfg);
                case "husk" -> applyHuskAbilities(mob, tier, cfg);
                case "drowned" -> applyDrownedAbilities(mob, tier, cfg);
                case "zombified_piglin" -> applyZombifiedPiglinAbilities(mob, tier, cfg);
                case "hoglin" -> applyHoglinAbilities(mob, tier, cfg);
                case "zoglin" -> applyZoglinAbilities(mob, tier, cfg);
                case "vindicator" -> applyVindicatorAbilities(mob, tier, cfg);
                case "wither_skeleton" -> applyWitherSkeletonAbilities(mob, tier, cfg);
                case "piglin" -> applyPiglinAbilities(mob, tier, cfg);
                case "stray" -> applyStrayAbilities(mob, tier, cfg);
                case "bogged" -> applyBoggedAbilities(mob, tier, cfg);
                case "witch" -> applyWitchAbilities(mob, tier, cfg);
                case "pillager" -> applyPillagerAbilities(mob, tier, cfg);
                case "guardian" -> applyGuardianAbilities(mob, tier, cfg);
                case "ravager" -> applyRavagerAbilities(mob, tier, cfg);
                case "silverfish" -> applySilverfishAbilities(mob, tier, cfg);
                case "endermite" -> applyEndermiteAbilities(mob, tier, cfg);
                default -> {}
            }
        } catch (Exception e) {
            Tribulation.LOGGER.warn("Failed applying abilities to {} tier {}", mobKey, tier, e);
        }
    }

    // ---- Zombie ----

    private static void applyZombieAbilities(Mob mob, int tier, TribulationConfig cfg) {
        if (tier >= 1 && cfg.abilities.zombieReinforcements) {
            addAttributeModifier(mob, Attributes.SPAWN_REINFORCEMENTS_CHANCE,
                    abilityId("zombie_reinforcements"), ZOMBIE_REINFORCEMENT_BONUS,
                    AttributeModifier.Operation.ADD_VALUE);
        }
        if (tier >= 3 && cfg.abilities.zombieDoorBreaking && mob instanceof Zombie zombie) {
            zombie.setCanBreakDoors(true);
        }
        if (tier >= 5 && cfg.abilities.zombieSprinting) {
            addAttributeModifier(mob, Attributes.MOVEMENT_SPEED,
                    abilityId("zombie_sprint"), SPRINT_SPEED_BONUS,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        }
    }

    // ---- Creeper ----

    private static void applyCreeperAbilities(Mob mob, int tier, TribulationConfig cfg) {
        if (!(mob instanceof Creeper creeper)) return;
        if (tier >= 1 && cfg.abilities.creeperShorterFuse) {
            ((CreeperAccessor) creeper).tribulation$setMaxSwell(CREEPER_SHORT_FUSE_TICKS);
        }
        if (tier >= 5 && cfg.abilities.creeperCharged) {
            if (mob.getRandom().nextFloat() < CREEPER_CHARGED_CHANCE) {
                creeper.getEntityData().set(CreeperAccessor.tribulation$getDataIsPowered(), true);
            }
        }
    }

    // ---- Skeleton ----

    private static void applySkeletonAbilities(Mob mob, int tier, TribulationConfig cfg) {
        if (tier >= 2 && cfg.abilities.skeletonSwordSwitch) {
            ItemStack current = mob.getMainHandItem();
            if (current.is(Items.BOW)) {
                mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
            }
        }
        if (tier >= 4 && cfg.abilities.skeletonFlameArrows) {
            applyInfiniteEffect(mob, MobEffects.FIRE_RESISTANCE, 0);
            mob.setRemainingFireTicks(Integer.MAX_VALUE);
        }
    }

    // ---- Spider ----

    private static void applySpiderAbilities(Mob mob, int tier, TribulationConfig cfg) {
        if (tier >= 2 && cfg.abilities.spiderWebPlacing) {
            mob.addTag("tribulation_web");
        }
        if (tier >= 3 && cfg.abilities.spiderCropTrample) {
            mob.addTag("tribulation_crop_trample");
        }
        if (tier >= 5 && cfg.abilities.spiderLeapAttack) {
            addAttributeModifier(mob, Attributes.JUMP_STRENGTH,
                    abilityId("spider_leap"), SPIDER_LEAP_BONUS,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        }
    }

    // ---- Cave Spider ----

    private static void applyCaveSpiderAbilities(Mob mob, int tier, TribulationConfig cfg) {
        if (tier >= 2 && cfg.abilities.spiderWebPlacing) {
            mob.addTag("tribulation_web");
        }
    }

    // ---- Husk ----

    private static void applyHuskAbilities(Mob mob, int tier, TribulationConfig cfg) {
        if (tier >= 4 && cfg.abilities.huskHunger) {
            mob.addTag("tribulation_hunger2");
        }
    }

    // ---- Drowned ----

    private static void applyDrownedAbilities(Mob mob, int tier, TribulationConfig cfg) {
        if (tier >= 2 && cfg.abilities.drownedTrident && mob.getMainHandItem().isEmpty()) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
        }
    }

    // ---- Zombified Piglin ----

    private static void applyZombifiedPiglinAbilities(Mob mob, int tier, TribulationConfig cfg) {
        if (tier >= 5 && cfg.abilities.zombifiedPiglinAggro) {
            addAttributeModifier(mob, Attributes.FOLLOW_RANGE,
                    abilityId("zombified_piglin_aggro"), ZOMBIFIED_PIGLIN_AGGRO_BONUS,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        }
    }

    // ---- Hoglin ----

    private static void applyHoglinAbilities(Mob mob, int tier, TribulationConfig cfg) {
        if (tier >= 1 && cfg.abilities.hoglinKnockbackResist) {
            addAttributeModifier(mob, Attributes.KNOCKBACK_RESISTANCE,
                    abilityId("hoglin_kb_resist"), HOGLIN_KB_BONUS,
                    AttributeModifier.Operation.ADD_VALUE);
        }
    }

    // ---- Zoglin ----

    private static void applyZoglinAbilities(Mob mob, int tier, TribulationConfig cfg) {
        if (tier >= 3 && cfg.abilities.zoglinFireResist) {
            applyInfiniteEffect(mob, MobEffects.FIRE_RESISTANCE, 0);
        }
    }

    // ---- Vindicator ----

    private static void applyVindicatorAbilities(Mob mob, int tier, TribulationConfig cfg) {
        if (tier >= 3 && cfg.abilities.vindicatorDoorBreaking) {
            // Vanilla vindicators only break doors on Hard (DOOR_BREAKING_PREDICATE).
            // BreakDoorGoalMixin relaxes that difficulty gate for tagged mobs, so the
            // existing goal fires on every difficulty — mirroring the zombie T3 beat.
            mob.addTag(TAG_DOOR_BREAKING);
        }
        if (tier >= 4 && cfg.abilities.vindicatorResistance) {
            applyInfiniteEffect(mob, MobEffects.DAMAGE_RESISTANCE, 0);
        }
    }

    // ---- Wither Skeleton ----

    private static void applyWitherSkeletonAbilities(Mob mob, int tier, TribulationConfig cfg) {
        if (tier >= 3 && cfg.abilities.witherSkeletonSprint) {
            addAttributeModifier(mob, Attributes.MOVEMENT_SPEED,
                    abilityId("wither_skeleton_sprint"), SPRINT_SPEED_BONUS,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        }
        if (tier >= 4 && cfg.abilities.witherSkeletonFireAspect) {
            ItemStack mainHand = mob.getMainHandItem();
            if (!mainHand.isEmpty()) {
                HolderLookup.RegistryLookup<Enchantment> lookup =
                        mob.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                lookup.get(Enchantments.FIRE_ASPECT).ifPresent(holder ->
                        mainHand.enchant(holder, 1));
            }
        }
    }

    // ---- Piglin ----

    private static void applyPiglinAbilities(Mob mob, int tier, TribulationConfig cfg) {
        if (tier >= 2 && cfg.abilities.piglinCrossbow && mob.getMainHandItem().isEmpty()) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        }
    }

    // ---- Stray ----

    private static void applyStrayAbilities(Mob mob, int tier, TribulationConfig cfg) {
        if (tier >= 2 && cfg.abilities.straySlownessUpgrade) {
            // StrayAbilityMixin upgrades the fired arrow's Slowness I to Slowness II.
            mob.addTag(TAG_SLOWNESS_ARROWS);
        }
    }

    // ---- Bogged ----

    private static void applyBoggedAbilities(Mob mob, int tier, TribulationConfig cfg) {
        if (tier >= 2 && cfg.abilities.boggedPoisonUpgrade) {
            // BoggedAbilityMixin upgrades the fired arrow's Poison I to Poison II.
            mob.addTag(TAG_POISON_ARROWS);
        }
    }

    // ---- Witch ----

    private static void applyWitchAbilities(Mob mob, int tier, TribulationConfig cfg) {
        if (tier >= 3 && cfg.abilities.witchLingeringPotions) {
            // WitchAbilityMixin swaps the thrown splash potion for a lingering one.
            mob.addTag(TAG_LINGERING_POTIONS);
        }
        if (tier >= 5 && cfg.abilities.witchAggressiveHealing) {
            // WitchAbilityMixin raises the per-tick heal-drink probability.
            mob.addTag(TAG_AGGRESSIVE_HEALING);
        }
    }

    // ---- Pillager ----

    private static void applyPillagerAbilities(Mob mob, int tier, TribulationConfig cfg) {
        if (tier >= 2 && cfg.abilities.pillagerQuickCharge) {
            enchantHeldCrossbow(mob, Enchantments.QUICK_CHARGE, 1);
        }
        if (tier >= 4 && cfg.abilities.pillagerMultishot) {
            enchantHeldCrossbow(mob, Enchantments.MULTISHOT, 1);
        }
    }

    // ---- Guardian ----

    private static void applyGuardianAbilities(Mob mob, int tier, TribulationConfig cfg) {
        if (tier >= 3 && cfg.abilities.guardianFasterBeam) {
            // GuardianBeamMixin shortens the beam's charge-to-fire duration.
            mob.addTag(TAG_GUARDIAN_BEAM);
        }
    }

    // ---- Ravager ----

    private static void applyRavagerAbilities(Mob mob, int tier, TribulationConfig cfg) {
        if (tier >= 3 && cfg.abilities.ravagerRoarExpansion) {
            // RavagerRoarMixin widens the roar's knockback radius.
            mob.addTag(TAG_RAVAGER_ROAR);
        }
    }

    // ---- Silverfish ----

    private static void applySilverfishAbilities(Mob mob, int tier, TribulationConfig cfg) {
        if (tier >= 2 && cfg.abilities.silverfishCallSleepers) {
            // Silverfish already wake infested-block friends in vanilla; the tag keeps
            // the ability discoverable in probes and shares the Endermite toggle.
            mob.addTag(TAG_CALL_SLEEPERS);
        }
    }

    // ---- Endermite ----

    private static void applyEndermiteAbilities(Mob mob, int tier, TribulationConfig cfg) {
        if (tier >= 2 && cfg.abilities.silverfishCallSleepers) {
            // SilverfishAbilityHandler (AFTER_DAMAGE) summons silverfish from nearby
            // infested blocks — the behavior an endermite has no vanilla goal for.
            mob.addTag(TAG_CALL_SLEEPERS);
        }
    }

    // ---- Helpers ----

    private static void enchantHeldCrossbow(Mob mob, ResourceKey<Enchantment> enchantment, int level) {
        ItemStack mainHand = mob.getMainHandItem();
        if (!mainHand.is(Items.CROSSBOW)) return;
        HolderLookup.RegistryLookup<Enchantment> lookup =
                mob.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        lookup.get(enchantment).ifPresent(holder -> mainHand.enchant(holder, level));
    }

    private static void addAttributeModifier(Mob mob, Holder<Attribute> attr, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addPermanentModifier(new AttributeModifier(id, amount, op));
    }

    private static void applyInfiniteEffect(Mob mob, Holder<MobEffect> effect, int amplifier) {
        mob.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, amplifier, false, false));
    }
}
