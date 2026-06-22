package com.rfizzle.tribulation.compat.modmenu;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            TribulationConfig config = Tribulation.getConfig();
            if (config == null) config = new TribulationConfig();
            TribulationConfig current = config;

            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Component.translatable("config.tribulation.title"));

            ConfigEntryBuilder entry = builder.entryBuilder();

            addGeneral(builder, entry, current);
            addHud(builder, entry, current);
            addScalingSources(builder, entry, current);
            addStatCaps(builder, entry, current);
            addTiers(builder, entry, current);
            addAbilities(builder, entry, current);
            addMobToggles(builder, entry, current);
            addDeathRelief(builder, entry, current);
            addShards(builder, entry, current);
            addHardcoreHearts(builder, entry, current);
            addSoulInventory(builder, entry, current);
            addTotems(builder, entry, current);
            addArmorEquipment(builder, entry, current);
            addWeaponEquipment(builder, entry, current);
            addSpecialZombies(builder, entry, current);
            addSpecialSkeletons(builder, entry, current);
            addBosses(builder, entry, current);
            addXpAndLoot(builder, entry, current);
            addTrialSpawner(builder, entry, current);
            addRaidScaling(builder, entry, current);

            builder.setSavingRunnable(() -> {
                current.save();
                Tribulation.reloadConfig();
            });

            return builder.build();
        };
    }

    private static void addGeneral(ConfigBuilder builder, ConfigEntryBuilder entry, TribulationConfig config) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.tribulation.category.general"));
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.general.max_level"),
                        config.general.maxLevel)
                .setDefaultValue(250).setMin(1)
                .setSaveConsumer(v -> config.general.maxLevel = v)
                .build());
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.general.level_up_ticks"),
                        config.general.levelUpTicks)
                .setDefaultValue(72000).setMin(1)
                .setSaveConsumer(v -> config.general.levelUpTicks = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.general.mob_detection_range"),
                        config.general.mobDetectionRange)
                .setDefaultValue(32.0).setMin(0.0)
                .setSaveConsumer(v -> config.general.mobDetectionRange = v)
                .build());
        cat.addEntry(entry.startSelector(
                        Component.translatable("config.tribulation.general.scaling_mode"),
                        TribulationConfig.ScalingMode.values(),
                        config.general.scalingMode)
                .setDefaultValue(TribulationConfig.ScalingMode.NEAREST)
                .setSaveConsumer(v -> config.general.scalingMode = v)
                .build());
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.general.notify_level_up"),
                        config.general.notifyLevelUp)
                .setDefaultValue(true)
                .setSaveConsumer(v -> config.general.notifyLevelUp = v)
                .build());
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.general.notify_level_up_show_tier"),
                        config.general.notifyLevelUpShowTier)
                .setDefaultValue(true)
                .setSaveConsumer(v -> config.general.notifyLevelUpShowTier = v)
                .build());
    }

    private static void addHud(ConfigBuilder builder, ConfigEntryBuilder entry, TribulationConfig config) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.tribulation.category.hud"));
        TribulationConfig.Hud hud = config.hud;
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.hud.enabled"),
                        hud.enabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> hud.enabled = v)
                .build());
        cat.addEntry(entry.startSelector(
                        Component.translatable("config.tribulation.hud.anchor"),
                        TribulationConfig.Anchor.values(),
                        hud.anchor)
                .setDefaultValue(TribulationConfig.Anchor.TOP_LEFT)
                .setSaveConsumer(v -> hud.anchor = v)
                .build());
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.hud.offset_x"),
                        hud.offsetX)
                .setDefaultValue(4).setMin(0)
                .setSaveConsumer(v -> hud.offsetX = v)
                .build());
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.hud.offset_y"),
                        hud.offsetY)
                .setDefaultValue(4).setMin(0)
                .setSaveConsumer(v -> hud.offsetY = v)
                .build());
    }

    private static void addScalingSources(ConfigBuilder builder, ConfigEntryBuilder entry, TribulationConfig config) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.tribulation.category.scaling_sources"));

        // Time
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.time_scaling.enabled"),
                        config.timeScaling.enabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> config.timeScaling.enabled = v)
                .build());

        // Distance
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.distance_scaling.enabled"),
                        config.distanceScaling.enabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> config.distanceScaling.enabled = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.distance_scaling.starting_distance"),
                        config.distanceScaling.startingDistance)
                .setDefaultValue(1000.0).setMin(0.0)
                .setSaveConsumer(v -> config.distanceScaling.startingDistance = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.distance_scaling.increasing_distance"),
                        config.distanceScaling.increasingDistance)
                .setDefaultValue(300.0).setMin(1.0)
                .setSaveConsumer(v -> config.distanceScaling.increasingDistance = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.distance_scaling.distance_factor"),
                        config.distanceScaling.distanceFactor)
                .setDefaultValue(0.1).setMin(0.0)
                .setSaveConsumer(v -> config.distanceScaling.distanceFactor = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.distance_scaling.max_distance_factor"),
                        config.distanceScaling.maxDistanceFactor)
                .setDefaultValue(1.5).setMin(0.0)
                .setSaveConsumer(v -> config.distanceScaling.maxDistanceFactor = v)
                .build());
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.distance_scaling.exclude_other_dimensions"),
                        config.distanceScaling.excludeInOtherDimensions)
                .setDefaultValue(true)
                .setSaveConsumer(v -> config.distanceScaling.excludeInOtherDimensions = v)
                .build());

        // Height
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.height_scaling.enabled"),
                        config.heightScaling.enabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> config.heightScaling.enabled = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.height_scaling.starting_height"),
                        config.heightScaling.startingHeight)
                .setDefaultValue(62.0)
                .setSaveConsumer(v -> config.heightScaling.startingHeight = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.height_scaling.height_distance"),
                        config.heightScaling.heightDistance)
                .setDefaultValue(30.0).setMin(1.0)
                .setSaveConsumer(v -> config.heightScaling.heightDistance = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.height_scaling.height_factor"),
                        config.heightScaling.heightFactor)
                .setDefaultValue(0.1).setMin(0.0)
                .setSaveConsumer(v -> config.heightScaling.heightFactor = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.height_scaling.max_height_factor"),
                        config.heightScaling.maxHeightFactor)
                .setDefaultValue(0.5).setMin(0.0)
                .setSaveConsumer(v -> config.heightScaling.maxHeightFactor = v)
                .build());
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.height_scaling.positive"),
                        config.heightScaling.positiveHeightScaling)
                .setDefaultValue(true)
                .setSaveConsumer(v -> config.heightScaling.positiveHeightScaling = v)
                .build());
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.height_scaling.negative"),
                        config.heightScaling.negativeHeightScaling)
                .setDefaultValue(true)
                .setSaveConsumer(v -> config.heightScaling.negativeHeightScaling = v)
                .build());
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.height_scaling.exclude_other_dimensions"),
                        config.heightScaling.excludeInOtherDimensions)
                .setDefaultValue(true)
                .setSaveConsumer(v -> config.heightScaling.excludeInOtherDimensions = v)
                .build());

        // Moon phase
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.moon_scaling.enabled"),
                        config.moonPhaseScaling.enabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> config.moonPhaseScaling.enabled = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.moon_scaling.max_bonus"),
                        config.moonPhaseScaling.maxBonus)
                .setDefaultValue(0.1).setMin(0.0)
                .setSaveConsumer(v -> config.moonPhaseScaling.maxBonus = v)
                .build());
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.moon_scaling.surface_only"),
                        config.moonPhaseScaling.surfaceOnly)
                .setDefaultValue(false)
                .setSaveConsumer(v -> config.moonPhaseScaling.surfaceOnly = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.moon_scaling.surface_y"),
                        config.moonPhaseScaling.surfaceY)
                .setDefaultValue(63.0)
                .setSaveConsumer(v -> config.moonPhaseScaling.surfaceY = v)
                .build());
    }

    private static void addStatCaps(ConfigBuilder builder, ConfigEntryBuilder entry, TribulationConfig config) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.tribulation.category.stat_caps"));
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.stat_caps.health"),
                        config.statCaps.maxFactorHealth)
                .setDefaultValue(4.0).setMin(0.0)
                .setSaveConsumer(v -> config.statCaps.maxFactorHealth = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.stat_caps.damage"),
                        config.statCaps.maxFactorDamage)
                .setDefaultValue(4.5).setMin(0.0)
                .setSaveConsumer(v -> config.statCaps.maxFactorDamage = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.stat_caps.speed"),
                        config.statCaps.maxFactorSpeed)
                .setDefaultValue(0.5).setMin(0.0)
                .setSaveConsumer(v -> config.statCaps.maxFactorSpeed = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.stat_caps.protection"),
                        config.statCaps.maxFactorProtection)
                .setDefaultValue(2.0).setMin(0.0)
                .setSaveConsumer(v -> config.statCaps.maxFactorProtection = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.stat_caps.follow_range"),
                        config.statCaps.maxFactorFollowRange)
                .setDefaultValue(1.5).setMin(0.0)
                .setSaveConsumer(v -> config.statCaps.maxFactorFollowRange = v)
                .build());
    }

    private static void addTiers(ConfigBuilder builder, ConfigEntryBuilder entry, TribulationConfig config) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.tribulation.category.tiers"));
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.tiers.tier1"),
                        config.tiers.tier1)
                .setDefaultValue(50).setMin(0)
                .setSaveConsumer(v -> config.tiers.tier1 = v)
                .build());
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.tiers.tier2"),
                        config.tiers.tier2)
                .setDefaultValue(100).setMin(0)
                .setSaveConsumer(v -> config.tiers.tier2 = v)
                .build());
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.tiers.tier3"),
                        config.tiers.tier3)
                .setDefaultValue(150).setMin(0)
                .setSaveConsumer(v -> config.tiers.tier3 = v)
                .build());
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.tiers.tier4"),
                        config.tiers.tier4)
                .setDefaultValue(200).setMin(0)
                .setSaveConsumer(v -> config.tiers.tier4 = v)
                .build());
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.tiers.tier5"),
                        config.tiers.tier5)
                .setDefaultValue(250).setMin(0)
                .setSaveConsumer(v -> config.tiers.tier5 = v)
                .build());
    }

    private static void addAbilities(ConfigBuilder builder, ConfigEntryBuilder entry, TribulationConfig config) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.tribulation.category.abilities"));
        TribulationConfig.Abilities a = config.abilities;

        addAbilityToggle(cat, entry, "zombie_reinforcements", a.zombieReinforcements, v -> a.zombieReinforcements = v);
        addAbilityToggle(cat, entry, "zombie_door_breaking", a.zombieDoorBreaking, v -> a.zombieDoorBreaking = v);
        addAbilityToggle(cat, entry, "zombie_sprinting", a.zombieSprinting, v -> a.zombieSprinting = v);
        addAbilityToggle(cat, entry, "creeper_shorter_fuse", a.creeperShorterFuse, v -> a.creeperShorterFuse = v);
        addAbilityToggle(cat, entry, "creeper_charged", a.creeperCharged, v -> a.creeperCharged = v);
        addAbilityToggle(cat, entry, "skeleton_sword_switch", a.skeletonSwordSwitch, v -> a.skeletonSwordSwitch = v);
        addAbilityToggle(cat, entry, "skeleton_flame_arrows", a.skeletonFlameArrows, v -> a.skeletonFlameArrows = v);
        addAbilityToggle(cat, entry, "spider_web_placing", a.spiderWebPlacing, v -> a.spiderWebPlacing = v);
        addAbilityToggle(cat, entry, "spider_crop_trample", a.spiderCropTrample, v -> a.spiderCropTrample = v);
        addAbilityToggle(cat, entry, "spider_leap_attack", a.spiderLeapAttack, v -> a.spiderLeapAttack = v);
        addAbilityToggle(cat, entry, "husk_hunger", a.huskHunger, v -> a.huskHunger = v);
        addAbilityToggle(cat, entry, "wither_skeleton_sprint", a.witherSkeletonSprint, v -> a.witherSkeletonSprint = v);
        addAbilityToggle(cat, entry, "wither_skeleton_fire_aspect", a.witherSkeletonFireAspect, v -> a.witherSkeletonFireAspect = v);
        addAbilityToggle(cat, entry, "drowned_trident", a.drownedTrident, v -> a.drownedTrident = v);
        addAbilityToggle(cat, entry, "hoglin_knockback_resist", a.hoglinKnockbackResist, v -> a.hoglinKnockbackResist = v);
        addAbilityToggle(cat, entry, "zoglin_fire_resist", a.zoglinFireResist, v -> a.zoglinFireResist = v);
        addAbilityToggle(cat, entry, "vindicator_resistance", a.vindicatorResistance, v -> a.vindicatorResistance = v);
        addAbilityToggle(cat, entry, "zombified_piglin_aggro", a.zombifiedPiglinAggro, v -> a.zombifiedPiglinAggro = v);
        addAbilityToggle(cat, entry, "piglin_crossbow", a.piglinCrossbow, v -> a.piglinCrossbow = v);
        addAbilityToggle(cat, entry, "stray_slowness_upgrade", a.straySlownessUpgrade, v -> a.straySlownessUpgrade = v);
        addAbilityToggle(cat, entry, "bogged_poison_upgrade", a.boggedPoisonUpgrade, v -> a.boggedPoisonUpgrade = v);
        addAbilityToggle(cat, entry, "witch_lingering_potions", a.witchLingeringPotions, v -> a.witchLingeringPotions = v);
        addAbilityToggle(cat, entry, "witch_aggressive_healing", a.witchAggressiveHealing, v -> a.witchAggressiveHealing = v);
        addAbilityToggle(cat, entry, "pillager_quick_charge", a.pillagerQuickCharge, v -> a.pillagerQuickCharge = v);
        addAbilityToggle(cat, entry, "pillager_multishot", a.pillagerMultishot, v -> a.pillagerMultishot = v);
        addAbilityToggle(cat, entry, "vindicator_door_breaking", a.vindicatorDoorBreaking, v -> a.vindicatorDoorBreaking = v);
        addAbilityToggle(cat, entry, "guardian_faster_beam", a.guardianFasterBeam, v -> a.guardianFasterBeam = v);
        addAbilityToggle(cat, entry, "ravager_roar_expansion", a.ravagerRoarExpansion, v -> a.ravagerRoarExpansion = v);
        addAbilityToggle(cat, entry, "silverfish_call_sleepers", a.silverfishCallSleepers, v -> a.silverfishCallSleepers = v);
    }

    private static void addAbilityToggle(ConfigCategory cat, ConfigEntryBuilder entry,
                                         String key, boolean current, java.util.function.Consumer<Boolean> save) {
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.abilities." + key),
                        current)
                .setDefaultValue(true)
                .setSaveConsumer(save)
                .build());
    }

    private static void addMobToggles(ConfigBuilder builder, ConfigEntryBuilder entry, TribulationConfig config) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.tribulation.category.mob_toggles"));

        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.mob_toggles.unlisted_hostile"),
                        config.unlistedHostileMobs.enabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> config.unlistedHostileMobs.enabled = v)
                .build());

        for (String mob : TribulationConfig.MOB_KEYS) {
            boolean value = config.mobToggles.getOrDefault(mob, true);
            cat.addEntry(entry.startBooleanToggle(
                            Component.translatable("config.tribulation.mob_toggles." + mob),
                            value)
                    .setDefaultValue(true)
                    .setSaveConsumer(v -> config.mobToggles.put(mob, v))
                    .build());
        }
    }

    private static void addDeathRelief(ConfigBuilder builder, ConfigEntryBuilder entry, TribulationConfig config) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.tribulation.category.death_relief"));
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.death_relief.enabled"),
                        config.deathRelief.enabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> config.deathRelief.enabled = v)
                .build());
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.death_relief.amount"),
                        config.deathRelief.amount)
                .setDefaultValue(2).setMin(0)
                .setSaveConsumer(v -> config.deathRelief.amount = v)
                .build());
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.death_relief.cooldown_ticks"),
                        config.deathRelief.cooldownTicks)
                .setDefaultValue(6000).setMin(0)
                .setSaveConsumer(v -> config.deathRelief.cooldownTicks = v)
                .build());
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.death_relief.minimum_level"),
                        config.deathRelief.minimumLevel)
                .setDefaultValue(0).setMin(0)
                .setSaveConsumer(v -> config.deathRelief.minimumLevel = v)
                .build());
    }

    private static void addShards(ConfigBuilder builder, ConfigEntryBuilder entry, TribulationConfig config) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.tribulation.category.shards"));
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.shards.enabled"),
                        config.shards.enabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> config.shards.enabled = v)
                .build());
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.shards.drop_start_level"),
                        config.shards.dropStartLevel)
                .setDefaultValue(25).setMin(0)
                .setSaveConsumer(v -> config.shards.dropStartLevel = v)
                .build());
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.shards.shard_power"),
                        config.shards.shardPower)
                .setDefaultValue(5).setMin(0)
                .setSaveConsumer(v -> config.shards.shardPower = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.shards.drop_chance"),
                        config.shards.dropChance)
                .setDefaultValue(0.005).setMin(0.0).setMax(1.0)
                .setSaveConsumer(v -> config.shards.dropChance = v)
                .build());
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.shards.side_effects"),
                        config.shards.sideEffects)
                .setDefaultValue(true)
                .setSaveConsumer(v -> config.shards.sideEffects = v)
                .build());
    }

    private static void addHardcoreHearts(ConfigBuilder builder, ConfigEntryBuilder entry, TribulationConfig config) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.tribulation.category.hardcore_hearts"));
        TribulationConfig.HardcoreHearts hh = config.hardcoreHearts;
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.hardcore_hearts.enabled"),
                        hh.enabled)
                .setDefaultValue(false)
                .setSaveConsumer(v -> hh.enabled = v)
                .build());
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.hardcore_hearts.hearts_lost_per_death"),
                        hh.heartsLostPerDeath)
                .setDefaultValue(2).setMin(1).setMax(20)
                .setSaveConsumer(v -> hh.heartsLostPerDeath = v)
                .build());
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.hardcore_hearts.minimum_hearts"),
                        hh.minimumHearts)
                .setDefaultValue(2).setMin(1).setMax(20)
                .setSaveConsumer(v -> hh.minimumHearts = v)
                .build());
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.hardcore_hearts.hearts_restored_per_fragment"),
                        hh.heartsRestoredPerFragment)
                .setDefaultValue(2).setMin(1).setMax(20)
                .setSaveConsumer(v -> hh.heartsRestoredPerFragment = v)
                .build());
    }

    private static void addSoulInventory(ConfigBuilder builder, ConfigEntryBuilder entry, TribulationConfig config) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.tribulation.category.soul_inventory"));
        TribulationConfig.SoulInventory si = config.soulInventory;
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.soul_inventory.enabled"),
                        si.enabled)
                .setDefaultValue(false)
                .setSaveConsumer(v -> si.enabled = v)
                .build());
        cat.addEntry(entry.startStrField(
                        Component.translatable("config.tribulation.soul_inventory.soulbound_enchantment"),
                        si.soulboundEnchantment)
                .setDefaultValue("tribulation:soulbound")
                .setSaveConsumer(v -> si.soulboundEnchantment = v)
                .build());
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.soul_inventory.destroy_xp"),
                        si.destroyXp)
                .setDefaultValue(false)
                .setSaveConsumer(v -> si.destroyXp = v)
                .build());
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.soul_inventory.respect_keep_inventory"),
                        si.respectKeepInventory)
                .setDefaultValue(true)
                .setSaveConsumer(v -> si.respectKeepInventory = v)
                .build());
    }

    private static void addTotems(ConfigBuilder builder, ConfigEntryBuilder entry, TribulationConfig config) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.tribulation.category.totems"));
        TribulationConfig.Totems totems = config.totems;
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.totems.counts_as_death_relief"),
                        totems.countsAsDeathRelief)
                .setDefaultValue(false)
                .setSaveConsumer(v -> totems.countsAsDeathRelief = v)
                .build());
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.totems.protects_hearts"),
                        totems.protectsHearts)
                .setDefaultValue(true)
                .setSaveConsumer(v -> totems.protectsHearts = v)
                .build());
    }

    private static void addSpecialZombies(ConfigBuilder builder, ConfigEntryBuilder entry, TribulationConfig config) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.tribulation.category.special_zombies"));
        TribulationConfig.SpecialZombies sz = config.specialZombies;
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.special_zombies.enabled"),
                        sz.enabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> sz.enabled = v)
                .build());
        cat.addEntry(entry.startIntSlider(
                        Component.translatable("config.tribulation.special_zombies.big_zombie_chance"),
                        sz.bigZombieChance, 0, 100)
                .setDefaultValue(10)
                .setSaveConsumer(v -> sz.bigZombieChance = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.special_zombies.big_zombie_size"),
                        sz.bigZombieSize)
                .setDefaultValue(1.3).setMin(1.0)
                .setSaveConsumer(v -> sz.bigZombieSize = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.special_zombies.big_zombie_bonus_health"),
                        sz.bigZombieBonusHealth)
                .setDefaultValue(10.0).setMin(0.0)
                .setSaveConsumer(v -> sz.bigZombieBonusHealth = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.special_zombies.big_zombie_bonus_damage"),
                        sz.bigZombieBonusDamage)
                .setDefaultValue(2.0).setMin(0.0)
                .setSaveConsumer(v -> sz.bigZombieBonusDamage = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.special_zombies.big_zombie_slowness"),
                        sz.bigZombieSlowness)
                .setDefaultValue(0.7).setMin(0.0)
                .setSaveConsumer(v -> sz.bigZombieSlowness = v)
                .build());
        cat.addEntry(entry.startIntSlider(
                        Component.translatable("config.tribulation.special_zombies.speed_zombie_chance"),
                        sz.speedZombieChance, 0, 100)
                .setDefaultValue(10)
                .setSaveConsumer(v -> sz.speedZombieChance = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.special_zombies.speed_zombie_speed_factor"),
                        sz.speedZombieSpeedFactor)
                .setDefaultValue(1.3).setMin(0.0)
                .setSaveConsumer(v -> sz.speedZombieSpeedFactor = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.special_zombies.speed_zombie_malus_health"),
                        sz.speedZombieMalusHealth)
                .setDefaultValue(10.0).setMin(0.0)
                .setSaveConsumer(v -> sz.speedZombieMalusHealth = v)
                .build());
    }

    private static void addSpecialSkeletons(ConfigBuilder builder, ConfigEntryBuilder entry, TribulationConfig config) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.tribulation.category.special_skeletons"));
        TribulationConfig.SpecialSkeletons sk = config.specialSkeletons;
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.special_skeletons.enabled"),
                        sk.enabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> sk.enabled = v)
                .build());
        cat.addEntry(entry.startIntSlider(
                        Component.translatable("config.tribulation.special_skeletons.deadeye_skeleton_chance"),
                        sk.deadeyeSkeletonChance, 0, 100)
                .setDefaultValue(10)
                .setSaveConsumer(v -> sk.deadeyeSkeletonChance = v)
                .build());
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.special_skeletons.deadeye_skeleton_attack_interval"),
                        sk.deadeyeSkeletonAttackInterval)
                .setDefaultValue(20).setMin(1)
                .setSaveConsumer(v -> sk.deadeyeSkeletonAttackInterval = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.special_skeletons.deadeye_skeleton_malus_health"),
                        sk.deadeyeSkeletonMalusHealth)
                .setDefaultValue(10.0).setMin(0.0)
                .setSaveConsumer(v -> sk.deadeyeSkeletonMalusHealth = v)
                .build());
        cat.addEntry(entry.startIntSlider(
                        Component.translatable("config.tribulation.special_skeletons.brute_skeleton_chance"),
                        sk.bruteSkeletonChance, 0, 100)
                .setDefaultValue(10)
                .setSaveConsumer(v -> sk.bruteSkeletonChance = v)
                .build());
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.special_skeletons.brute_skeleton_attack_interval"),
                        sk.bruteSkeletonAttackInterval)
                .setDefaultValue(60).setMin(1)
                .setSaveConsumer(v -> sk.bruteSkeletonAttackInterval = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.special_skeletons.brute_skeleton_bonus_health"),
                        sk.bruteSkeletonBonusHealth)
                .setDefaultValue(10.0).setMin(0.0)
                .setSaveConsumer(v -> sk.bruteSkeletonBonusHealth = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.special_skeletons.brute_skeleton_bonus_knockback_resistance"),
                        sk.bruteSkeletonBonusKnockbackResistance)
                .setDefaultValue(0.5).setMin(0.0).setMax(1.0)
                .setSaveConsumer(v -> sk.bruteSkeletonBonusKnockbackResistance = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.special_skeletons.brute_skeleton_size"),
                        sk.bruteSkeletonSize)
                .setDefaultValue(1.3).setMin(1.0)
                .setSaveConsumer(v -> sk.bruteSkeletonSize = v)
                .build());
    }

    private static void addBosses(ConfigBuilder builder, ConfigEntryBuilder entry, TribulationConfig config) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.tribulation.category.bosses"));
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.bosses.affect_bosses"),
                        config.bosses.affectBosses)
                .setDefaultValue(true)
                .setSaveConsumer(v -> config.bosses.affectBosses = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.bosses.max_factor"),
                        config.bosses.bossMaxFactor)
                .setDefaultValue(3.0).setMin(0.0)
                .setSaveConsumer(v -> config.bosses.bossMaxFactor = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.bosses.distance_factor"),
                        config.bosses.bossDistanceFactor)
                .setDefaultValue(0.1).setMin(0.0)
                .setSaveConsumer(v -> config.bosses.bossDistanceFactor = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.bosses.time_factor"),
                        config.bosses.bossTimeFactor)
                .setDefaultValue(0.3).setMin(0.0)
                .setSaveConsumer(v -> config.bosses.bossTimeFactor = v)
                .build());
    }

    private static void addXpAndLoot(ConfigBuilder builder, ConfigEntryBuilder entry, TribulationConfig config) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.tribulation.category.xp_and_loot"));
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.xp_and_loot.extra_xp"),
                        config.xpAndLoot.extraXp)
                .setDefaultValue(true)
                .setSaveConsumer(v -> config.xpAndLoot.extraXp = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.xp_and_loot.max_xp_factor"),
                        config.xpAndLoot.maxXpFactor)
                .setDefaultValue(2.0).setMin(0.0)
                .setSaveConsumer(v -> config.xpAndLoot.maxXpFactor = v)
                .build());
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.xp_and_loot.drop_more_loot"),
                        config.xpAndLoot.dropMoreLoot)
                .setDefaultValue(false)
                .setSaveConsumer(v -> config.xpAndLoot.dropMoreLoot = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.xp_and_loot.more_loot_chance"),
                        config.xpAndLoot.moreLootChance)
                .setDefaultValue(0.02).setMin(0.0)
                .setSaveConsumer(v -> config.xpAndLoot.moreLootChance = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.xp_and_loot.max_loot_chance"),
                        config.xpAndLoot.maxLootChance)
                .setDefaultValue(0.7).setMin(0.0).setMax(1.0)
                .setSaveConsumer(v -> config.xpAndLoot.maxLootChance = v)
                .build());
    }

    private static void addArmorEquipment(ConfigBuilder builder, ConfigEntryBuilder entry, TribulationConfig config) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.tribulation.category.armor"));
        TribulationConfig.ArmorEquipment ae = config.armorEquipment;

        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.armor.enabled"),
                        ae.enabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> ae.enabled = v)
                .build());
        cat.addEntry(entry.startSelector(
                        Component.translatable("config.tribulation.armor.material_roll_mode"),
                        TribulationConfig.MaterialRollMode.values(),
                        ae.materialRollMode)
                .setDefaultValue(TribulationConfig.MaterialRollMode.PER_MOB)
                .setSaveConsumer(v -> ae.materialRollMode = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.armor.drop_chance"),
                        ae.armorDropChance)
                .setDefaultValue(0.0).setMin(0.0).setMax(2.0)
                .setSaveConsumer(v -> ae.armorDropChance = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.armor.armor_ceiling"),
                        ae.armorCeiling)
                .setDefaultValue(24.0).setMin(0.0)
                .setSaveConsumer(v -> ae.armorCeiling = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.armor.toughness_ceiling"),
                        ae.toughnessCeiling)
                .setDefaultValue(15.0).setMin(0.0)
                .setSaveConsumer(v -> ae.toughnessCeiling = v)
                .build());
    }

    private static void addWeaponEquipment(ConfigBuilder builder, ConfigEntryBuilder entry, TribulationConfig config) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.tribulation.category.weapon"));
        TribulationConfig.WeaponEquipment we = config.weaponEquipment;

        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.weapon.enabled"),
                        we.enabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> we.enabled = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.weapon.drop_chance"),
                        we.weaponDropChance)
                .setDefaultValue(0.0).setMin(0.0).setMax(2.0)
                .setSaveConsumer(v -> we.weaponDropChance = v)
                .build());
        cat.addEntry(entry.startDoubleField(
                        Component.translatable("config.tribulation.weapon.damage_ceiling"),
                        we.damageCeiling)
                .setDefaultValue(20.0).setMin(0.0)
                .setSaveConsumer(v -> we.damageCeiling = v)
                .build());
    }

    private static void addTrialSpawner(ConfigBuilder builder, ConfigEntryBuilder entry, TribulationConfig config) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.tribulation.category.trial_spawner"));
        TribulationConfig.TrialSpawnerConfig ts = config.trialSpawner;

        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.trial_spawner.enabled"),
                        ts.enabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> ts.enabled = v)
                .build());
        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.trial_spawner.ominous_enabled"),
                        ts.ominousUpgrade.enabled)
                .setDefaultValue(false)
                .setSaveConsumer(v -> ts.ominousUpgrade.enabled = v)
                .build());
        cat.addEntry(entry.startFloatField(
                        Component.translatable("config.tribulation.trial_spawner.ominous_chance"),
                        ts.ominousUpgrade.chance)
                .setDefaultValue(0.10f).setMin(0.0f).setMax(1.0f)
                .setSaveConsumer(v -> ts.ominousUpgrade.chance = v)
                .build());
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.trial_spawner.ominous_min_tier"),
                        ts.ominousUpgrade.minimumTier)
                .setDefaultValue(3).setMin(0)
                .setSaveConsumer(v -> ts.ominousUpgrade.minimumTier = v)
                .build());
    }

    private static void addRaidScaling(ConfigBuilder builder, ConfigEntryBuilder entry, TribulationConfig config) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.tribulation.category.raid_scaling"));
        TribulationConfig.RaidScaling rs = config.raidScaling;

        cat.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.tribulation.raid_scaling.enabled"),
                        rs.enabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> rs.enabled = v)
                .build());
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.raid_scaling.patrol_bonus_rate"),
                        rs.patrolBonusRate)
                .setDefaultValue(2).setMin(0)
                .setSaveConsumer(v -> rs.patrolBonusRate = v)
                .build());
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.raid_scaling.extra_wave_tier_threshold"),
                        rs.extraWaveTierThreshold)
                .setDefaultValue(4).setMin(0)
                .setSaveConsumer(v -> rs.extraWaveTierThreshold = v)
                .build());
        cat.addEntry(entry.startIntField(
                        Component.translatable("config.tribulation.raid_scaling.extra_wave_count"),
                        rs.extraWaveCount)
                .setDefaultValue(1).setMin(0)
                .setSaveConsumer(v -> rs.extraWaveCount = v)
                .build());
    }
}
