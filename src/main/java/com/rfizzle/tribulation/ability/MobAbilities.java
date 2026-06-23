package com.rfizzle.tribulation.ability;

import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.mixin.CreeperAccessor;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * The declarative registry of every tier ability. {@link AbilityManager}
 * iterates it server-side to apply abilities to scaled mobs; the client tier
 * detail panel queries it to list what mobs can do at the player's tier. Both
 * read this one list, so the gameplay behavior and the displayed behavior can
 * never diverge.
 *
 * <p>The list is built once at class load via {@link List#of} and is
 * unmodifiable thereafter. Per {@code mc-shared-state}, each query snapshots the
 * static {@link #REGISTRY} reference into a local at entry so the class stays
 * correct if the registry ever becomes reloadable.
 *
 * <p>Entries are ordered by mob key (in {@link TribulationConfig#MOB_KEYS}
 * order) then unlock tier, so panel grouping and {@link #forMob} are
 * deterministic.
 */
public final class MobAbilities {

    public static final List<MobAbility> REGISTRY = List.of(
            // ---- Zombie ----
            new MobAbility("zombie", "zombie_reinforcements", 1,
                    a -> a.zombieReinforcements,
                    (mob, cfg) -> AbilityManager.addAttributeModifier(mob, Attributes.SPAWN_REINFORCEMENTS_CHANCE,
                            AbilityManager.abilityId("zombie_reinforcements"), AbilityManager.ZOMBIE_REINFORCEMENT_BONUS,
                            AttributeModifier.Operation.ADD_VALUE)),
            new MobAbility("zombie", "zombie_door_breaking", 3,
                    a -> a.zombieDoorBreaking,
                    (mob, cfg) -> {
                        if (mob instanceof Zombie zombie) {
                            zombie.setCanBreakDoors(true);
                        }
                    }),
            new MobAbility("zombie", "zombie_sprinting", 5,
                    a -> a.zombieSprinting,
                    (mob, cfg) -> AbilityManager.addAttributeModifier(mob, Attributes.MOVEMENT_SPEED,
                            AbilityManager.abilityId("zombie_sprint"), AbilityManager.SPRINT_SPEED_BONUS,
                            AttributeModifier.Operation.ADD_MULTIPLIED_BASE)),

            // ---- Skeleton ----
            new MobAbility("skeleton", "skeleton_sword_switch", 2,
                    a -> a.skeletonSwordSwitch,
                    (mob, cfg) -> {
                        if (mob.getMainHandItem().is(Items.BOW)) {
                            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
                        }
                    }),
            new MobAbility("skeleton", "skeleton_flame_arrows", 4,
                    a -> a.skeletonFlameArrows,
                    (mob, cfg) -> {
                        AbilityManager.applyInfiniteEffect(mob, MobEffects.FIRE_RESISTANCE, 0);
                        mob.setRemainingFireTicks(Integer.MAX_VALUE);
                    }),

            // ---- Creeper ----
            new MobAbility("creeper", "creeper_shorter_fuse", 1,
                    a -> a.creeperShorterFuse,
                    (mob, cfg) -> {
                        if (mob instanceof Creeper creeper) {
                            ((CreeperAccessor) creeper).tribulation$setMaxSwell(AbilityManager.CREEPER_SHORT_FUSE_TICKS);
                        }
                    }),
            new MobAbility("creeper", "creeper_charged", 5,
                    a -> a.creeperCharged,
                    (mob, cfg) -> {
                        if (mob instanceof Creeper creeper
                                && creeper.getRandom().nextFloat() < AbilityManager.CREEPER_CHARGED_CHANCE) {
                            creeper.getEntityData().set(CreeperAccessor.tribulation$getDataIsPowered(), true);
                        }
                    }),

            // ---- Spider ----
            new MobAbility("spider", "spider_web_placing", 2,
                    a -> a.spiderWebPlacing,
                    (mob, cfg) -> mob.addTag("tribulation_web")),
            new MobAbility("spider", "spider_crop_trample", 3,
                    a -> a.spiderCropTrample,
                    (mob, cfg) -> mob.addTag("tribulation_crop_trample")),
            new MobAbility("spider", "spider_leap_attack", 5,
                    a -> a.spiderLeapAttack,
                    (mob, cfg) -> AbilityManager.addAttributeModifier(mob, Attributes.JUMP_STRENGTH,
                            AbilityManager.abilityId("spider_leap"), AbilityManager.SPIDER_LEAP_BONUS,
                            AttributeModifier.Operation.ADD_MULTIPLIED_BASE)),

            // ---- Cave Spider (shares the spider web toggle) ----
            new MobAbility("cave_spider", "spider_web_placing", 2,
                    a -> a.spiderWebPlacing,
                    (mob, cfg) -> mob.addTag("tribulation_web")),

            // ---- Endermite (shares the silverfish call-sleepers toggle) ----
            new MobAbility("endermite", "silverfish_call_sleepers", 2,
                    a -> a.silverfishCallSleepers,
                    (mob, cfg) -> mob.addTag(AbilityManager.TAG_CALL_SLEEPERS)),

            // ---- Silverfish ----
            new MobAbility("silverfish", "silverfish_call_sleepers", 2,
                    a -> a.silverfishCallSleepers,
                    (mob, cfg) -> mob.addTag(AbilityManager.TAG_CALL_SLEEPERS)),

            // ---- Drowned ----
            new MobAbility("drowned", "drowned_trident", 2,
                    a -> a.drownedTrident,
                    (mob, cfg) -> {
                        if (mob.getMainHandItem().isEmpty()) {
                            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
                        }
                    }),

            // ---- Husk ----
            new MobAbility("husk", "husk_hunger", 4,
                    a -> a.huskHunger,
                    (mob, cfg) -> mob.addTag("tribulation_hunger2")),

            // ---- Stray ----
            new MobAbility("stray", "stray_slowness_upgrade", 2,
                    a -> a.straySlownessUpgrade,
                    (mob, cfg) -> mob.addTag(AbilityManager.TAG_SLOWNESS_ARROWS)),

            // ---- Pillager ----
            new MobAbility("pillager", "pillager_quick_charge", 2,
                    a -> a.pillagerQuickCharge,
                    (mob, cfg) -> AbilityManager.enchantHeldCrossbow(mob, Enchantments.QUICK_CHARGE, 1)),
            new MobAbility("pillager", "pillager_multishot", 4,
                    a -> a.pillagerMultishot,
                    (mob, cfg) -> AbilityManager.enchantHeldCrossbow(mob, Enchantments.MULTISHOT, 1)),

            // ---- Vindicator ----
            new MobAbility("vindicator", "vindicator_door_breaking", 3,
                    a -> a.vindicatorDoorBreaking,
                    (mob, cfg) -> mob.addTag(AbilityManager.TAG_DOOR_BREAKING)),
            new MobAbility("vindicator", "vindicator_resistance", 4,
                    a -> a.vindicatorResistance,
                    (mob, cfg) -> AbilityManager.applyInfiniteEffect(mob, MobEffects.DAMAGE_RESISTANCE, 0)),

            // ---- Witch ----
            new MobAbility("witch", "witch_lingering_potions", 3,
                    a -> a.witchLingeringPotions,
                    (mob, cfg) -> mob.addTag(AbilityManager.TAG_LINGERING_POTIONS)),
            new MobAbility("witch", "witch_aggressive_healing", 5,
                    a -> a.witchAggressiveHealing,
                    (mob, cfg) -> mob.addTag(AbilityManager.TAG_AGGRESSIVE_HEALING)),

            // ---- Wither Skeleton ----
            new MobAbility("wither_skeleton", "wither_skeleton_sprint", 3,
                    a -> a.witherSkeletonSprint,
                    (mob, cfg) -> AbilityManager.addAttributeModifier(mob, Attributes.MOVEMENT_SPEED,
                            AbilityManager.abilityId("wither_skeleton_sprint"), AbilityManager.SPRINT_SPEED_BONUS,
                            AttributeModifier.Operation.ADD_MULTIPLIED_BASE)),
            new MobAbility("wither_skeleton", "wither_skeleton_fire_aspect", 4,
                    a -> a.witherSkeletonFireAspect,
                    (mob, cfg) -> {
                        ItemStack mainHand = mob.getMainHandItem();
                        if (!mainHand.isEmpty()) {
                            HolderLookup.RegistryLookup<Enchantment> lookup =
                                    mob.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                            lookup.get(Enchantments.FIRE_ASPECT).ifPresent(holder -> mainHand.enchant(holder, 1));
                        }
                    }),

            // ---- Guardian ----
            new MobAbility("guardian", "guardian_faster_beam", 3,
                    a -> a.guardianFasterBeam,
                    (mob, cfg) -> mob.addTag(AbilityManager.TAG_GUARDIAN_BEAM)),

            // ---- Hoglin ----
            new MobAbility("hoglin", "hoglin_knockback_resist", 1,
                    a -> a.hoglinKnockbackResist,
                    (mob, cfg) -> AbilityManager.addAttributeModifier(mob, Attributes.KNOCKBACK_RESISTANCE,
                            AbilityManager.abilityId("hoglin_kb_resist"), AbilityManager.HOGLIN_KB_BONUS,
                            AttributeModifier.Operation.ADD_VALUE)),

            // ---- Zoglin ----
            new MobAbility("zoglin", "zoglin_fire_resist", 3,
                    a -> a.zoglinFireResist,
                    (mob, cfg) -> AbilityManager.applyInfiniteEffect(mob, MobEffects.FIRE_RESISTANCE, 0)),

            // ---- Ravager ----
            new MobAbility("ravager", "ravager_roar_expansion", 3,
                    a -> a.ravagerRoarExpansion,
                    (mob, cfg) -> mob.addTag(AbilityManager.TAG_RAVAGER_ROAR)),

            // ---- Piglin ----
            new MobAbility("piglin", "piglin_crossbow", 2,
                    a -> a.piglinCrossbow,
                    (mob, cfg) -> {
                        if (mob.getMainHandItem().isEmpty()) {
                            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
                        }
                    }),

            // ---- Zombified Piglin ----
            new MobAbility("zombified_piglin", "zombified_piglin_aggro", 5,
                    a -> a.zombifiedPiglinAggro,
                    (mob, cfg) -> AbilityManager.addAttributeModifier(mob, Attributes.FOLLOW_RANGE,
                            AbilityManager.abilityId("zombified_piglin_aggro"), AbilityManager.ZOMBIFIED_PIGLIN_AGGRO_BONUS,
                            AttributeModifier.Operation.ADD_MULTIPLIED_BASE)),

            // ---- Bogged ----
            new MobAbility("bogged", "bogged_poison_upgrade", 2,
                    a -> a.boggedPoisonUpgrade,
                    (mob, cfg) -> mob.addTag(AbilityManager.TAG_POISON_ARROWS))
    );

    private MobAbilities() {}

    /**
     * Every ability declared for {@code mobKey}, in unlock-tier order. Empty if
     * the mob key has no abilities.
     */
    public static List<MobAbility> forMob(String mobKey) {
        List<MobAbility> registry = REGISTRY;
        List<MobAbility> out = new ArrayList<>();
        for (MobAbility ability : registry) {
            if (ability.mobKey().equals(mobKey)) {
                out.add(ability);
            }
        }
        return out;
    }

    /**
     * Every ability across all mobs that is unlocked at {@code tier} and enabled
     * by the config toggles. Used by the tier detail panel to list what scaled
     * mobs can currently do.
     */
    public static List<MobAbility> activeAt(int tier, TribulationConfig cfg) {
        if (cfg == null || cfg.abilities == null) return List.of();
        List<MobAbility> registry = REGISTRY;
        List<MobAbility> out = new ArrayList<>();
        for (MobAbility ability : registry) {
            if (ability.unlockTier() <= tier && ability.enabled().test(cfg.abilities)) {
                out.add(ability);
            }
        }
        return out;
    }

    /**
     * The abilities of one mob that are unlocked at {@code tier} and enabled,
     * in unlock-tier order. The panel groups its listing by calling this per
     * mob key.
     */
    public static List<MobAbility> activeForMob(String mobKey, int tier, TribulationConfig cfg) {
        if (cfg == null || cfg.abilities == null) return List.of();
        List<MobAbility> registry = REGISTRY;
        List<MobAbility> out = new ArrayList<>();
        for (MobAbility ability : registry) {
            if (ability.mobKey().equals(mobKey)
                    && ability.unlockTier() <= tier
                    && ability.enabled().test(cfg.abilities)) {
                out.add(ability);
            }
        }
        return out;
    }
}
