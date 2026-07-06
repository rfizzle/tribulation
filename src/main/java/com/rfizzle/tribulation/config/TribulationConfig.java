package com.rfizzle.tribulation.config;

import com.rfizzle.tribulation.Tribulation;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TribulationConfig {
    private static final String CONFIG_FILENAME = "tribulation.json";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    public static final String[] MOB_KEYS = {
            "zombie", "skeleton", "creeper", "spider", "cave_spider",
            "endermite", "silverfish", "drowned", "husk", "stray",
            "pillager", "vindicator", "witch", "wither_skeleton", "guardian",
            "hoglin", "zoglin", "ravager", "piglin", "zombified_piglin", "bogged"
    };

    public int configVersion = 14;
    public General general = new General();
    public TimeScaling timeScaling = new TimeScaling();
    public DistanceScaling distanceScaling = new DistanceScaling();
    public HeightScaling heightScaling = new HeightScaling();
    public MoonPhaseScaling moonPhaseScaling = new MoonPhaseScaling();
    public BloodMoon bloodMoon = new BloodMoon();
    public GroupHealthBonus groupHealthBonus = new GroupHealthBonus();
    public Map<String, Integer> dimensionOffsets = defaultDimensionOffsets();
    public Map<String, Integer> biomeOffsets = defaultBiomeOffsets();
    public StructureBoosts structureBoosts = new StructureBoosts();
    public StatCaps statCaps = new StatCaps();
    public Totems totems = new Totems();
    public DeathRelief deathRelief = new DeathRelief();
    public LevelDecay levelDecay = new LevelDecay();
    public Shards shards = new Shards();
    public HardcoreHearts hardcoreHearts = new HardcoreHearts();
    public SoulInventory soulInventory = new SoulInventory();
    public Map<String, MobScaling> scaling = defaultScaling();
    public UnlistedHostileMobs unlistedHostileMobs = new UnlistedHostileMobs();
    public SpecialZombies specialZombies = new SpecialZombies();
    public SpecialSkeletons specialSkeletons = new SpecialSkeletons();
    public Bosses bosses = new Bosses();
    public Xp xp = new Xp();
    public Tiers tiers = new Tiers();
    public Map<String, Boolean> mobToggles = defaultMobToggles();
    public Abilities abilities = new Abilities();
    public ArmorEquipment armorEquipment = new ArmorEquipment();
    public WeaponEquipment weaponEquipment = new WeaponEquipment();
    public Champions champions = new Champions();
    public TrialSpawnerConfig trialSpawner = new TrialSpawnerConfig();
    public RaidScaling raidScaling = new RaidScaling();
    public PackTactics packTactics = new PackTactics();
    public EnvironmentalPressure environmentalPressure = new EnvironmentalPressure();
    public ThreatParticles threatParticles = new ThreatParticles();
    public Hud hud = new Hud();

    public static TribulationConfig load() {
        return load(configPath());
    }

    static TribulationConfig load(Path path) {
        if (!Files.exists(path)) {
            Tribulation.LOGGER.info("Config file missing; creating default at {}", path);
            TribulationConfig config = new TribulationConfig();
            config.save(path);
            return config;
        }
        try {
            String content = Files.readString(path);
            if (content.isBlank()) {
                Tribulation.LOGGER.warn("Config file at {} was empty; using defaults", path);
                TribulationConfig fresh = new TribulationConfig();
                fresh.save(path);
                return fresh;
            }

            JsonElement element = JsonParser.parseString(content);
            if (element == null || element.isJsonNull() || !element.isJsonObject()) {
                Tribulation.LOGGER.warn("Config file at {} is not a JSON object; using defaults", path);
                TribulationConfig fresh = new TribulationConfig();
                fresh.save(path);
                return fresh;
            }

            JsonObject raw = element.getAsJsonObject();
            boolean migrated = ConfigMigrator.migrate(raw);

            TribulationConfig config = GSON.fromJson(raw, TribulationConfig.class);
            if (config == null) {
                Tribulation.LOGGER.warn("Config at {} deserialized to null; using defaults", path);
                TribulationConfig fresh = new TribulationConfig();
                fresh.save(path);
                return fresh;
            }
            config.fillDefaults();
            config.validate();

            if (migrated) {
                config.save(path);
            }

            return config;
        } catch (JsonSyntaxException e) {
            Tribulation.LOGGER.error("Failed to parse config at {}; using defaults (existing file left untouched)", path, e);
            TribulationConfig fallback = new TribulationConfig();
            fallback.fillDefaults();
            fallback.validate();
            return fallback;
        } catch (IOException e) {
            Tribulation.LOGGER.error("Failed to read config at {}; using defaults", path, e);
            TribulationConfig fallback = new TribulationConfig();
            fallback.fillDefaults();
            fallback.validate();
            return fallback;
        }
    }

    public void save() {
        save(configPath());
    }

    void save(Path path) {
        // Write to a sibling .tmp then atomically rename, so a crash mid-write can
        // never leave a truncated tribulation.json (which would load as defaults and
        // silently wipe the player's settings). Fall back to a plain move where
        // atomic moves aren't supported, and clean up the orphan tmp on failure.
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(tmp, GSON.toJson(this));
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Tribulation.LOGGER.error("Failed to save config to {}", path, e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException cleanup) {
                Tribulation.LOGGER.warn("Failed to clean up orphan config tmp {}", tmp, cleanup);
            }
        }
    }

    public MobScaling getMobScaling(String mobKey) {
        MobScaling result = scaling.get(mobKey);
        if (result != null) return result;
        MobScaling zombie = scaling.get("zombie");
        return zombie != null ? zombie : new MobScaling();
    }

    public boolean isMobEnabled(String mobKey) {
        return mobToggles.getOrDefault(mobKey, Boolean.FALSE);
    }

    /**
     * Flat level offset added to the effective scaling level for mobs in the
     * given dimension (see {@link com.rfizzle.tribulation.scaling.ScalingEngine#getEffectiveLevel}).
     * Returns {@code 0} when the dimension has no configured entry, so unlisted
     * dimensions (the Overworld by default) are unaffected. Null-safe against
     * a missing map or a {@code null} JSON value.
     */
    public int getDimensionOffset(ResourceLocation dimension) {
        if (dimensionOffsets == null || dimension == null) return 0;
        Integer offset = dimensionOffsets.get(dimension.toString());
        return offset != null ? offset : 0;
    }

    /**
     * Parsed lookup structure for {@link #biomeOffsets}, rebuilt lazily when
     * the map's contents change (config reload swaps the whole object, but
     * tests mutate the map in place). {@code transient} so Gson never
     * serializes it.
     */
    private transient volatile BiomeOffsetResolver biomeOffsetResolver;

    /** True when at least one biome offset entry is configured — lets the spawn hot path skip the biome lookup entirely. */
    public boolean hasBiomeOffsets() {
        return biomeOffsets != null && !biomeOffsets.isEmpty();
    }

    /**
     * Flat level offset added to the effective scaling level for mobs in the
     * given biome, stacking additively with {@link #getDimensionOffset} (see
     * {@link com.rfizzle.tribulation.scaling.ScalingEngine#getEffectiveLevel}).
     * Keys in {@link #biomeOffsets} are biome IDs ({@code minecraft:deep_dark})
     * or {@code #}-prefixed biome tags; an exact ID entry wins over tags, and
     * the largest offset among matching tags applies otherwise. Returns
     * {@code 0} for unlisted biomes, so scaling elsewhere is unaffected.
     */
    public int getBiomeOffset(Holder<Biome> biome) {
        Map<String, Integer> offsets = biomeOffsets;
        if (offsets == null || offsets.isEmpty() || biome == null) return 0;
        BiomeOffsetResolver resolver = biomeOffsetResolver;
        if (resolver == null || !resolver.matches(offsets)) {
            resolver = BiomeOffsetResolver.build(offsets);
            biomeOffsetResolver = resolver;
        }
        return resolver.offsetFor(biome);
    }

    /**
     * Parsed lookup structure for {@link StructureBoosts#boosts}, rebuilt
     * lazily when the map's contents change (config reload swaps the whole
     * object, but tests mutate the map in place). {@code transient} so Gson
     * never serializes it.
     */
    private transient volatile StructureBoostResolver structureBoostResolver;

    /** True when at least one structure boost entry is configured — lets the spawn hot path skip structure lookups entirely. */
    public boolean hasStructureBoosts() {
        return structureBoosts != null
                && structureBoosts.boosts != null
                && !structureBoosts.boosts.isEmpty();
    }

    /**
     * Flat level boost added to the effective scaling level for mobs spawning
     * inside (or within {@link StructureBoosts#marginBlocks} of) the given
     * structure's bounds, stacking additively with the dimension and biome
     * offsets (see {@link com.rfizzle.tribulation.scaling.ScalingEngine#getEffectiveLevel}).
     * Keys in {@link StructureBoosts#boosts} are structure IDs
     * ({@code minecraft:fortress}) or {@code #}-prefixed structure tags; an
     * exact ID entry wins over tags, and the largest boost among matching tags
     * applies otherwise. Returns {@code 0} for unlisted structures.
     */
    public int getStructureBoost(Holder<Structure> structure) {
        StructureBoosts section = structureBoosts;
        if (section == null || structure == null) return 0;
        Map<String, Integer> boosts = section.boosts;
        if (boosts == null || boosts.isEmpty()) return 0;
        StructureBoostResolver resolver = structureBoostResolver;
        if (resolver == null || !resolver.matches(boosts)) {
            resolver = StructureBoostResolver.build(boosts);
            structureBoostResolver = resolver;
        }
        return resolver.boostFor(structure);
    }

    /**
     * Resolve the {@link MobScaling} for an entity, applying the precedence
     * defined in DESIGN.md:
     * <ol>
     *   <li>Full-ID override in {@code scaling} (works for any namespace)</li>
     *   <li>Vanilla path lookup in {@code scaling}, gated by {@code mobToggles}
     *       (explicit {@code false} returns {@code null} — does NOT fall through
     *       to the modded fallback)</li>
     *   <li>Modded fallback — when {@code unlistedHostileMobs.enabled} is true,
     *       the entity is an instance of {@link Monster}, and its namespace is
     *       not listed in {@code excludedNamespaces}</li>
     *   <li>Otherwise {@code null} — no scaling applied</li>
     * </ol>
     */
    public MobScaling resolveScalingForEntity(ResourceLocation typeId, Mob mob) {
        return resolveScalingForEntity(typeId, mob instanceof Monster);
    }

    /**
     * Pure-logic variant of {@link #resolveScalingForEntity(ResourceLocation, Mob)}
     * that takes the {@code instanceof Monster} result as a boolean so it can be
     * exercised by unit tests without constructing real {@link Mob} instances.
     */
    public MobScaling resolveScalingForEntity(ResourceLocation typeId, boolean isMonster) {
        if (typeId == null) return null;

        // 1. Full-ID override — hand-tuned escape hatch for any namespace.
        MobScaling override = scaling != null ? scaling.get(typeId.toString()) : null;
        if (override != null) return override;

        // 2. Vanilla path lookup — only for minecraft: namespace, gated by mobToggles.
        //    A vanilla mob with its toggle disabled returns null explicitly so the
        //    modded fallback does NOT take over ("explicit no-scale wins").
        if ("minecraft".equals(typeId.getNamespace())) {
            String path = typeId.getPath();
            if (!isMobEnabled(path)) {
                return null;
            }
            return scaling != null ? scaling.get(path) : null;
        }

        // 3. Modded fallback — health + damage only for unlisted hostile mobs.
        if (unlistedHostileMobs == null || !unlistedHostileMobs.enabled) return null;
        if (!isMonster) return null;
        List<String> excluded = unlistedHostileMobs.excludedNamespaces;
        if (excluded != null && excluded.contains(typeId.getNamespace())) return null;
        return unlistedHostileMobs.scaling;
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILENAME);
    }

    private void fillDefaults() {
        if (general == null) general = new General();
        if (general.scalingMode == null) general.scalingMode = ScalingMode.NEAREST;
        if (general.excludedEntities == null) general.excludedEntities = new ArrayList<>();
        if (timeScaling == null) timeScaling = new TimeScaling();
        if (distanceScaling == null) distanceScaling = new DistanceScaling();
        if (heightScaling == null) heightScaling = new HeightScaling();
        if (moonPhaseScaling == null) moonPhaseScaling = new MoonPhaseScaling();
        if (bloodMoon == null) bloodMoon = new BloodMoon();
        if (groupHealthBonus == null) groupHealthBonus = new GroupHealthBonus();
        if (statCaps == null) statCaps = new StatCaps();
        if (totems == null) totems = new Totems();
        if (deathRelief == null) deathRelief = new DeathRelief();
        if (levelDecay == null) levelDecay = new LevelDecay();
        if (shards == null) shards = new Shards();
        if (hardcoreHearts == null) hardcoreHearts = new HardcoreHearts();
        if (soulInventory == null) soulInventory = new SoulInventory();
        if (specialZombies == null) specialZombies = new SpecialZombies();
        if (specialSkeletons == null) specialSkeletons = new SpecialSkeletons();
        if (bosses == null) bosses = new Bosses();
        if (xp == null) xp = new Xp();
        if (tiers == null) tiers = new Tiers();
        if (abilities == null) abilities = new Abilities();
        if (armorEquipment == null) armorEquipment = new ArmorEquipment();
        if (armorEquipment.tiers == null) {
            armorEquipment.tiers = ArmorEquipment.defaultArmorTiers();
        } else {
            Map<String, ArmorTier> defaults = ArmorEquipment.defaultArmorTiers();
            for (Map.Entry<String, ArmorTier> entry : defaults.entrySet()) {
                armorEquipment.tiers.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        for (ArmorTier at : armorEquipment.tiers.values()) {
            if (at.materialWeights == null) at.materialWeights = new LinkedHashMap<>();
        }

        if (weaponEquipment == null) weaponEquipment = new WeaponEquipment();
        if (weaponEquipment.tiers == null) {
            weaponEquipment.tiers = WeaponEquipment.defaultWeaponTiers();
        } else {
            Map<String, WeaponTier> defaults = WeaponEquipment.defaultWeaponTiers();
            for (Map.Entry<String, WeaponTier> entry : defaults.entrySet()) {
                weaponEquipment.tiers.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        for (WeaponTier wt : weaponEquipment.tiers.values()) {
            if (wt.materialWeights == null) wt.materialWeights = new LinkedHashMap<>();
        }

        if (hud == null) hud = new Hud();
        if (hud.anchor == null) hud.anchor = Anchor.TOP_LEFT;

        if (champions == null) champions = new Champions();
        if (champions.affixes == null) champions.affixes = new Champions.Affixes();

        if (trialSpawner == null) trialSpawner = new TrialSpawnerConfig();
        if (trialSpawner.ominousUpgrade == null) trialSpawner.ominousUpgrade = new TrialSpawnerConfig.OminousUpgrade();

        if (raidScaling == null) raidScaling = new RaidScaling();

        if (packTactics == null) packTactics = new PackTactics();
        if (packTactics.eligibleMobs == null) {
            packTactics.eligibleMobs = PackTactics.defaultEligibleMobs();
        }

        if (threatParticles == null) threatParticles = new ThreatParticles();

        if (environmentalPressure == null) environmentalPressure = new EnvironmentalPressure();
        if (environmentalPressure.debilitatingStrikes == null) {
            environmentalPressure.debilitatingStrikes = new EnvironmentalPressure.DebilitatingStrikes();
        }
        if (environmentalPressure.oppressiveNights == null) {
            environmentalPressure.oppressiveNights = new EnvironmentalPressure.OppressiveNights();
        }

        if (scaling == null) {
            scaling = defaultScaling();
        } else {
            Map<String, MobScaling> defaults = defaultScaling();
            for (Map.Entry<String, MobScaling> entry : defaults.entrySet()) {
                scaling.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        if (mobToggles == null) {
            mobToggles = defaultMobToggles();
        } else {
            Map<String, Boolean> defaults = defaultMobToggles();
            for (Map.Entry<String, Boolean> entry : defaults.entrySet()) {
                mobToggles.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        if (dimensionOffsets == null) {
            dimensionOffsets = defaultDimensionOffsets();
        } else {
            Map<String, Integer> defaults = defaultDimensionOffsets();
            for (Map.Entry<String, Integer> entry : defaults.entrySet()) {
                dimensionOffsets.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        if (biomeOffsets == null) {
            biomeOffsets = defaultBiomeOffsets();
        } else {
            Map<String, Integer> defaults = defaultBiomeOffsets();
            for (Map.Entry<String, Integer> entry : defaults.entrySet()) {
                biomeOffsets.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        // Unlike dimensionOffsets/biomeOffsets, an existing boosts map is NOT
        // re-seeded with defaults: an explicitly emptied map is the documented
        // off-switch for the whole feature (issue #124), so only null-heal.
        if (structureBoosts == null) {
            structureBoosts = new StructureBoosts();
        }
        if (structureBoosts.boosts == null) {
            structureBoosts.boosts = StructureBoosts.defaultBoosts();
        }

        if (unlistedHostileMobs == null) {
            unlistedHostileMobs = new UnlistedHostileMobs();
        }
        if (unlistedHostileMobs.excludedNamespaces == null) {
            unlistedHostileMobs.excludedNamespaces = new ArrayList<>();
        }
        if (unlistedHostileMobs.scaling == null) {
            unlistedHostileMobs.scaling = UnlistedHostileMobs.defaultScaling();
        }
    }

    /**
     * Bounds every numeric field to its valid range, logging each correction.
     * Called after load (post-deserialize) and again on ModMenu save, so the
     * on-disk file can never hold out-of-range values no matter how it was
     * populated.
     */
    public void validate() {
        if (general.maxLevel < 1) {
            Tribulation.LOGGER.warn("general.maxLevel must be >= 1, got {}; clamped to 1", general.maxLevel);
            general.maxLevel = 1;
        }
        if (general.levelUpTicks < 1) {
            Tribulation.LOGGER.warn("general.levelUpTicks must be >= 1, got {}; clamped to 1", general.levelUpTicks);
            general.levelUpTicks = 1;
        }
        if (general.mobDetectionRange < 0) {
            Tribulation.LOGGER.warn("general.mobDetectionRange must be >= 0, got {}; clamped to 0", general.mobDetectionRange);
            general.mobDetectionRange = 0;
        }

        distanceScaling.startingDistance = clampNonNegative("distanceScaling.startingDistance", distanceScaling.startingDistance);
        distanceScaling.increasingDistance = clampPositive("distanceScaling.increasingDistance", distanceScaling.increasingDistance);
        distanceScaling.distanceFactor = clampNonNegative("distanceScaling.distanceFactor", distanceScaling.distanceFactor);
        distanceScaling.maxDistanceFactor = clampNonNegative("distanceScaling.maxDistanceFactor", distanceScaling.maxDistanceFactor);

        heightScaling.heightDistance = clampPositive("heightScaling.heightDistance", heightScaling.heightDistance);
        heightScaling.heightFactor = clampNonNegative("heightScaling.heightFactor", heightScaling.heightFactor);
        heightScaling.maxHeightFactor = clampNonNegative("heightScaling.maxHeightFactor", heightScaling.maxHeightFactor);

        moonPhaseScaling.maxBonus = clampNonNegative("moonPhaseScaling.maxBonus", moonPhaseScaling.maxBonus);

        bloodMoon.chance = clampUnit("bloodMoon.chance", bloodMoon.chance);
        bloodMoon.moonBonusMultiplier = clampAtLeastOne("bloodMoon.moonBonusMultiplier", bloodMoon.moonBonusMultiplier);
        bloodMoon.spawnCapMultiplier = clampAtLeastOne("bloodMoon.spawnCapMultiplier", bloodMoon.spawnCapMultiplier);

        groupHealthBonus.perPlayerBonus = clampNonNegative("groupHealthBonus.perPlayerBonus", groupHealthBonus.perPlayerBonus);
        groupHealthBonus.maxBonus = clampNonNegative("groupHealthBonus.maxBonus", groupHealthBonus.maxBonus);

        if (dimensionOffsets != null) {
            for (Map.Entry<String, Integer> entry : dimensionOffsets.entrySet()) {
                Integer offset = entry.getValue();
                if (offset == null) {
                    entry.setValue(0);
                } else if (offset < 0) {
                    Tribulation.LOGGER.warn("dimensionOffsets.{} must be >= 0, got {}; clamped to 0", entry.getKey(), offset);
                    entry.setValue(0);
                }
            }
        }

        if (biomeOffsets != null) {
            biomeOffsets.entrySet().removeIf(entry -> {
                if (!isValidBiomeOffsetKey(entry.getKey())) {
                    Tribulation.LOGGER.warn("biomeOffsets key '{}' is not a valid biome id or #tag; entry skipped", entry.getKey());
                    return true;
                }
                return false;
            });
            for (Map.Entry<String, Integer> entry : biomeOffsets.entrySet()) {
                Integer offset = entry.getValue();
                if (offset == null) {
                    entry.setValue(0);
                } else if (offset < 0) {
                    Tribulation.LOGGER.warn("biomeOffsets.{} must be >= 0, got {}; clamped to 0", entry.getKey(), offset);
                    entry.setValue(0);
                }
            }
        }

        if (structureBoosts != null) {
            if (structureBoosts.marginBlocks < 0) {
                Tribulation.LOGGER.warn("structureBoosts.marginBlocks must be >= 0, got {}; clamped to 0", structureBoosts.marginBlocks);
                structureBoosts.marginBlocks = 0;
            } else if (structureBoosts.marginBlocks > StructureBoosts.MAX_MARGIN_BLOCKS) {
                Tribulation.LOGGER.warn("structureBoosts.marginBlocks must be <= {}, got {}; clamped to {}",
                        StructureBoosts.MAX_MARGIN_BLOCKS, structureBoosts.marginBlocks, StructureBoosts.MAX_MARGIN_BLOCKS);
                structureBoosts.marginBlocks = StructureBoosts.MAX_MARGIN_BLOCKS;
            }
            if (structureBoosts.boosts != null) {
                structureBoosts.boosts.entrySet().removeIf(entry -> {
                    if (!isValidIdOrTagKey(entry.getKey())) {
                        Tribulation.LOGGER.warn("structureBoosts.boosts key '{}' is not a valid structure id or #tag; entry skipped", entry.getKey());
                        return true;
                    }
                    return false;
                });
                for (Map.Entry<String, Integer> entry : structureBoosts.boosts.entrySet()) {
                    Integer boost = entry.getValue();
                    if (boost == null) {
                        entry.setValue(0);
                    } else if (boost < 0) {
                        Tribulation.LOGGER.warn("structureBoosts.boosts.{} must be >= 0, got {}; clamped to 0", entry.getKey(), boost);
                        entry.setValue(0);
                    }
                }
            }
        }

        statCaps.maxFactorHealth = clampNonNegative("statCaps.maxFactorHealth", statCaps.maxFactorHealth);
        statCaps.maxFactorDamage = clampNonNegative("statCaps.maxFactorDamage", statCaps.maxFactorDamage);
        statCaps.maxFactorSpeed = clampNonNegative("statCaps.maxFactorSpeed", statCaps.maxFactorSpeed);
        statCaps.maxFactorProtection = clampNonNegative("statCaps.maxFactorProtection", statCaps.maxFactorProtection);
        statCaps.maxFactorFollowRange = clampNonNegative("statCaps.maxFactorFollowRange", statCaps.maxFactorFollowRange);

        if (deathRelief.amount < 0) {
            Tribulation.LOGGER.warn("deathRelief.amount must be >= 0, got {}; clamped to 0", deathRelief.amount);
            deathRelief.amount = 0;
        }
        if (deathRelief.cooldownTicks < 0) {
            Tribulation.LOGGER.warn("deathRelief.cooldownTicks must be >= 0, got {}; clamped to 0", deathRelief.cooldownTicks);
            deathRelief.cooldownTicks = 0;
        }
        if (deathRelief.minimumLevel < 0) {
            Tribulation.LOGGER.warn("deathRelief.minimumLevel must be >= 0, got {}; clamped to 0", deathRelief.minimumLevel);
            deathRelief.minimumLevel = 0;
        }

        levelDecay.graceDays = clampNonNegative("levelDecay.graceDays", levelDecay.graceDays);
        levelDecay.levelsPerDay = clampNonNegative("levelDecay.levelsPerDay", levelDecay.levelsPerDay);
        levelDecay.floor = clampAtLeast("levelDecay.floor", levelDecay.floor, 0);
        if (levelDecay.floor > general.maxLevel) {
            Tribulation.LOGGER.warn("levelDecay.floor must be <= general.maxLevel ({}), got {}; clamped",
                    general.maxLevel, levelDecay.floor);
            levelDecay.floor = general.maxLevel;
        }

        if (shards.dropStartLevel < 0) {
            Tribulation.LOGGER.warn("shards.dropStartLevel must be >= 0, got {}; clamped to 0", shards.dropStartLevel);
            shards.dropStartLevel = 0;
        }
        if (shards.shardPower < 0) {
            Tribulation.LOGGER.warn("shards.shardPower must be >= 0, got {}; clamped to 0", shards.shardPower);
            shards.shardPower = 0;
        }
        shards.dropChance = clampUnit("shards.dropChance", shards.dropChance);

        if (hardcoreHearts.heartsLostPerDeath < 1) {
            Tribulation.LOGGER.warn("hardcoreHearts.heartsLostPerDeath must be >= 1, got {}; clamped to 1", hardcoreHearts.heartsLostPerDeath);
            hardcoreHearts.heartsLostPerDeath = 1;
        }
        if (hardcoreHearts.heartsLostPerDeath > 20) {
            Tribulation.LOGGER.warn("hardcoreHearts.heartsLostPerDeath must be <= 20, got {}; clamped to 20", hardcoreHearts.heartsLostPerDeath);
            hardcoreHearts.heartsLostPerDeath = 20;
        }
        if (hardcoreHearts.minimumHearts < 1) {
            Tribulation.LOGGER.warn("hardcoreHearts.minimumHearts must be >= 1, got {}; clamped to 1", hardcoreHearts.minimumHearts);
            hardcoreHearts.minimumHearts = 1;
        }
        if (hardcoreHearts.minimumHearts > 20) {
            Tribulation.LOGGER.warn("hardcoreHearts.minimumHearts must be <= 20, got {}; clamped to 20", hardcoreHearts.minimumHearts);
            hardcoreHearts.minimumHearts = 20;
        }
        if (hardcoreHearts.heartsRestoredPerFragment < 1) {
            Tribulation.LOGGER.warn("hardcoreHearts.heartsRestoredPerFragment must be >= 1, got {}; clamped to 1", hardcoreHearts.heartsRestoredPerFragment);
            hardcoreHearts.heartsRestoredPerFragment = 1;
        }
        if (hardcoreHearts.heartsRestoredPerFragment > 20) {
            Tribulation.LOGGER.warn("hardcoreHearts.heartsRestoredPerFragment must be <= 20, got {}; clamped to 20", hardcoreHearts.heartsRestoredPerFragment);
            hardcoreHearts.heartsRestoredPerFragment = 20;
        }
        for (Map.Entry<String, MobScaling> entry : scaling.entrySet()) {
            clampMobScaling("scaling." + entry.getKey(), entry.getValue());
        }
        clampMobScaling("unlistedHostileMobs.scaling", unlistedHostileMobs.scaling);

        specialZombies.bigZombieChance = clampPercent("specialZombies.bigZombieChance", specialZombies.bigZombieChance);
        specialZombies.speedZombieChance = clampPercent("specialZombies.speedZombieChance", specialZombies.speedZombieChance);
        specialZombies.bigZombieSize = clampPositive("specialZombies.bigZombieSize", specialZombies.bigZombieSize);
        specialZombies.bigZombieSlowness = clampNonNegative("specialZombies.bigZombieSlowness", specialZombies.bigZombieSlowness);
        specialZombies.speedZombieSpeedFactor = clampNonNegative("specialZombies.speedZombieSpeedFactor", specialZombies.speedZombieSpeedFactor);

        specialSkeletons.deadeyeSkeletonChance = clampPercent("specialSkeletons.deadeyeSkeletonChance", specialSkeletons.deadeyeSkeletonChance);
        specialSkeletons.bruteSkeletonChance = clampPercent("specialSkeletons.bruteSkeletonChance", specialSkeletons.bruteSkeletonChance);
        specialSkeletons.deadeyeSkeletonAttackInterval = clampAtLeast("specialSkeletons.deadeyeSkeletonAttackInterval", specialSkeletons.deadeyeSkeletonAttackInterval, 1);
        specialSkeletons.bruteSkeletonAttackInterval = clampAtLeast("specialSkeletons.bruteSkeletonAttackInterval", specialSkeletons.bruteSkeletonAttackInterval, 1);
        specialSkeletons.deadeyeSkeletonMalusHealth = clampNonNegative("specialSkeletons.deadeyeSkeletonMalusHealth", specialSkeletons.deadeyeSkeletonMalusHealth);
        specialSkeletons.bruteSkeletonBonusHealth = clampNonNegative("specialSkeletons.bruteSkeletonBonusHealth", specialSkeletons.bruteSkeletonBonusHealth);
        specialSkeletons.bruteSkeletonBonusKnockbackResistance = clampUnit("specialSkeletons.bruteSkeletonBonusKnockbackResistance", specialSkeletons.bruteSkeletonBonusKnockbackResistance);
        specialSkeletons.bruteSkeletonSize = clampPositive("specialSkeletons.bruteSkeletonSize", specialSkeletons.bruteSkeletonSize);

        bosses.bossMaxFactor = clampNonNegative("bosses.bossMaxFactor", bosses.bossMaxFactor);
        bosses.bossDistanceFactor = clampNonNegative("bosses.bossDistanceFactor", bosses.bossDistanceFactor);
        bosses.bossTimeFactor = clampNonNegative("bosses.bossTimeFactor", bosses.bossTimeFactor);

        xp.xpMultiplier = clampNonNegative("xp.xpMultiplier", xp.xpMultiplier);

        if (tiers.tier1 < 0) tiers.tier1 = 0;
        if (tiers.tier2 < tiers.tier1) tiers.tier2 = tiers.tier1;
        if (tiers.tier3 < tiers.tier2) tiers.tier3 = tiers.tier2;
        if (tiers.tier4 < tiers.tier3) tiers.tier4 = tiers.tier3;
        if (tiers.tier5 < tiers.tier4) tiers.tier5 = tiers.tier4;

        // Drop chance is [0,2], not [0,1]: a value >= 1.0 is a valid request for a
        // guaranteed + pristine drop (vanilla's PRESERVE threshold).
        armorEquipment.armorDropChance = clampNonNegative("armorEquipment.armorDropChance", armorEquipment.armorDropChance);
        if (armorEquipment.armorDropChance > 2.0) {
            Tribulation.LOGGER.warn("armorEquipment.armorDropChance must be <= 2.0, got {}; clamped to 2.0", armorEquipment.armorDropChance);
            armorEquipment.armorDropChance = 2.0;
        }
        armorEquipment.armorCeiling = clampNonNegative("armorEquipment.armorCeiling", armorEquipment.armorCeiling);
        armorEquipment.toughnessCeiling = clampNonNegative("armorEquipment.toughnessCeiling", armorEquipment.toughnessCeiling);

        for (Map.Entry<String, ArmorTier> entry : armorEquipment.tiers.entrySet()) {
            ArmorTier at = entry.getValue();
            at.wearChancePercent = clampPercent("armorEquipment.tiers." + entry.getKey() + ".wearChancePercent", at.wearChancePercent);
            at.slotCoveragePercent = clampPercent("armorEquipment.tiers." + entry.getKey() + ".slotCoveragePercent", at.slotCoveragePercent);
            at.enchantChancePercent = clampPercent("armorEquipment.tiers." + entry.getKey() + ".enchantChancePercent", at.enchantChancePercent);
            if (at.maxProtectionLevel < 0) at.maxProtectionLevel = 0;
            at.materialWeights.entrySet().removeIf(e -> {
                if (e.getValue() < 0) {
                    Tribulation.LOGGER.warn("armorEquipment.tiers.{}.materialWeights.{} must be >= 0, got {}; removing", entry.getKey(), e.getKey(), e.getValue());
                    return true;
                }
                return false;
            });
        }

        weaponEquipment.weaponDropChance = clampNonNegative("weaponEquipment.weaponDropChance", weaponEquipment.weaponDropChance);
        if (weaponEquipment.weaponDropChance > 2.0) {
            Tribulation.LOGGER.warn("weaponEquipment.weaponDropChance must be <= 2.0, got {}; clamped to 2.0", weaponEquipment.weaponDropChance);
            weaponEquipment.weaponDropChance = 2.0;
        }
        weaponEquipment.damageCeiling = clampNonNegative("weaponEquipment.damageCeiling", weaponEquipment.damageCeiling);

        champions.championChance = clampUnit("champions.championChance", champions.championChance);
        if (champions.levelThreshold < 0) {
            Tribulation.LOGGER.warn("champions.levelThreshold must be >= 0, got {}; clamped to 0", champions.levelThreshold);
            champions.levelThreshold = 0;
        }
        champions.maxAffixes = clampAtLeast("champions.maxAffixes", champions.maxAffixes, 1);
        champions.healthMultiplier = clampAtLeastOne("champions.healthMultiplier", champions.healthMultiplier);
        champions.damageMultiplier = clampAtLeastOne("champions.damageMultiplier", champions.damageMultiplier);
        champions.xpMultiplier = clampAtLeastOne("champions.xpMultiplier", champions.xpMultiplier);
        if (champions.bonusLootRolls < 0) {
            Tribulation.LOGGER.warn("champions.bonusLootRolls must be >= 0, got {}; clamped to 0", champions.bonusLootRolls);
            champions.bonusLootRolls = 0;
        }
        champions.affixes.vampiricHealFraction = clampUnit("champions.affixes.vampiricHealFraction", champions.affixes.vampiricHealFraction);
        champions.affixes.explosivePower = clampNonNegative("champions.affixes.explosivePower", champions.affixes.explosivePower);
        champions.affixes.knockbackAuraStrength = clampNonNegative("champions.affixes.knockbackAuraStrength", champions.affixes.knockbackAuraStrength);
        champions.affixes.knockbackAuraRadius = clampNonNegative("champions.affixes.knockbackAuraRadius", champions.affixes.knockbackAuraRadius);
        champions.affixes.knockbackAuraIntervalTicks = clampAtLeast("champions.affixes.knockbackAuraIntervalTicks", champions.affixes.knockbackAuraIntervalTicks, 1);
        champions.affixes.thornsFraction = clampNonNegative("champions.affixes.thornsFraction", champions.affixes.thornsFraction);
        champions.affixes.regenHealthPerSecond = clampNonNegative("champions.affixes.regenHealthPerSecond", champions.affixes.regenHealthPerSecond);

        trialSpawner.ominousUpgrade.chance = (float) clampUnit("trialSpawner.ominousUpgrade.chance", trialSpawner.ominousUpgrade.chance);
        if (trialSpawner.ominousUpgrade.minimumTier < 0) {
            trialSpawner.ominousUpgrade.minimumTier = 0;
        }

        if (raidScaling.patrolBonusRate < 0) {
            Tribulation.LOGGER.warn("raidScaling.patrolBonusRate must be >= 0, got {}; clamped to 0", raidScaling.patrolBonusRate);
            raidScaling.patrolBonusRate = 0;
        }
        if (raidScaling.extraWaveTierThreshold < 0) {
            Tribulation.LOGGER.warn("raidScaling.extraWaveTierThreshold must be >= 0, got {}; clamped to 0", raidScaling.extraWaveTierThreshold);
            raidScaling.extraWaveTierThreshold = 0;
        }
        if (raidScaling.extraWaveCount < 0) {
            Tribulation.LOGGER.warn("raidScaling.extraWaveCount must be >= 0, got {}; clamped to 0", raidScaling.extraWaveCount);
            raidScaling.extraWaveCount = 0;
        }

        packTactics.tierThreshold = clampAtLeast("packTactics.tierThreshold", packTactics.tierThreshold, 0);
        packTactics.alertRadius = clampNonNegative("packTactics.alertRadius", packTactics.alertRadius);
        if (packTactics.alertRadius > PackTactics.MAX_ALERT_RADIUS) {
            Tribulation.LOGGER.warn("packTactics.alertRadius must be <= {}, got {}; clamped to {}",
                    PackTactics.MAX_ALERT_RADIUS, packTactics.alertRadius, PackTactics.MAX_ALERT_RADIUS);
            packTactics.alertRadius = PackTactics.MAX_ALERT_RADIUS;
        }
        packTactics.groupSizeBonus = clampAtLeast("packTactics.groupSizeBonus", packTactics.groupSizeBonus, 0);
        if (packTactics.groupSizeBonus > PackTactics.MAX_GROUP_SIZE_BONUS) {
            Tribulation.LOGGER.warn("packTactics.groupSizeBonus must be <= {}, got {}; clamped to {}",
                    PackTactics.MAX_GROUP_SIZE_BONUS, packTactics.groupSizeBonus, PackTactics.MAX_GROUP_SIZE_BONUS);
            packTactics.groupSizeBonus = PackTactics.MAX_GROUP_SIZE_BONUS;
        }

        if (threatParticles.particleFrequencyTicks < 1) {
            Tribulation.LOGGER.warn("threatParticles.particleFrequencyTicks must be >= 1, got {}; clamped to 1", threatParticles.particleFrequencyTicks);
            threatParticles.particleFrequencyTicks = 1;
        }
        if (threatParticles.minimumTier < 0) {
            Tribulation.LOGGER.warn("threatParticles.minimumTier must be >= 0, got {}; clamped to 0", threatParticles.minimumTier);
            threatParticles.minimumTier = 0;
        }

        EnvironmentalPressure.DebilitatingStrikes strikes = environmentalPressure.debilitatingStrikes;
        strikes.tierThreshold = clampAtLeast("environmentalPressure.debilitatingStrikes.tierThreshold", strikes.tierThreshold, 0);
        strikes.weaknessDurationTicks = clampIntRange("environmentalPressure.debilitatingStrikes.weaknessDurationTicks",
                strikes.weaknessDurationTicks, 1, EnvironmentalPressure.DebilitatingStrikes.MAX_EFFECT_DURATION_TICKS);
        strikes.weaknessAmplifier = clampIntRange("environmentalPressure.debilitatingStrikes.weaknessAmplifier",
                strikes.weaknessAmplifier, 0, EnvironmentalPressure.DebilitatingStrikes.MAX_EFFECT_AMPLIFIER);
        strikes.slownessDurationTicks = clampIntRange("environmentalPressure.debilitatingStrikes.slownessDurationTicks",
                strikes.slownessDurationTicks, 1, EnvironmentalPressure.DebilitatingStrikes.MAX_EFFECT_DURATION_TICKS);
        strikes.slownessAmplifier = clampIntRange("environmentalPressure.debilitatingStrikes.slownessAmplifier",
                strikes.slownessAmplifier, 0, EnvironmentalPressure.DebilitatingStrikes.MAX_EFFECT_AMPLIFIER);

        EnvironmentalPressure.OppressiveNights nights = environmentalPressure.oppressiveNights;
        nights.tierThreshold = clampAtLeast("environmentalPressure.oppressiveNights.tierThreshold", nights.tierThreshold, 0);
        nights.maxDarkness = clampNonNegative("environmentalPressure.oppressiveNights.maxDarkness", nights.maxDarkness);
        if (nights.maxDarkness > EnvironmentalPressure.OppressiveNights.MAX_NIGHT_DARKNESS) {
            Tribulation.LOGGER.warn("environmentalPressure.oppressiveNights.maxDarkness must be <= {}, got {}; clamped to {}",
                    EnvironmentalPressure.OppressiveNights.MAX_NIGHT_DARKNESS, nights.maxDarkness,
                    EnvironmentalPressure.OppressiveNights.MAX_NIGHT_DARKNESS);
            nights.maxDarkness = EnvironmentalPressure.OppressiveNights.MAX_NIGHT_DARKNESS;
        }
        nights.followRangeMultiplier = clampAtLeastOne("environmentalPressure.oppressiveNights.followRangeMultiplier",
                nights.followRangeMultiplier);
        if (nights.followRangeMultiplier > EnvironmentalPressure.OppressiveNights.MAX_FOLLOW_RANGE_MULTIPLIER) {
            Tribulation.LOGGER.warn("environmentalPressure.oppressiveNights.followRangeMultiplier must be <= {}, got {}; clamped to {}",
                    EnvironmentalPressure.OppressiveNights.MAX_FOLLOW_RANGE_MULTIPLIER, nights.followRangeMultiplier,
                    EnvironmentalPressure.OppressiveNights.MAX_FOLLOW_RANGE_MULTIPLIER);
            nights.followRangeMultiplier = EnvironmentalPressure.OppressiveNights.MAX_FOLLOW_RANGE_MULTIPLIER;
        }

        for (Map.Entry<String, WeaponTier> entry : weaponEquipment.tiers.entrySet()) {
            WeaponTier wt = entry.getValue();
            wt.wearChancePercent = clampPercent("weaponEquipment.tiers." + entry.getKey() + ".wearChancePercent", wt.wearChancePercent);
            wt.enchantChancePercent = clampPercent("weaponEquipment.tiers." + entry.getKey() + ".enchantChancePercent", wt.enchantChancePercent);
            if (wt.maxEnchantmentLevel < 0) wt.maxEnchantmentLevel = 0;
            wt.materialWeights.entrySet().removeIf(e -> {
                if (e.getValue() < 0) {
                    Tribulation.LOGGER.warn("weaponEquipment.tiers.{}.materialWeights.{} must be >= 0, got {}; removing", entry.getKey(), e.getKey(), e.getValue());
                    return true;
                }
                return false;
            });
        }
    }

    private static void clampMobScaling(String prefix, MobScaling m) {
        if (m == null) return;
        m.healthRate = clampNonNegative(prefix + ".healthRate", m.healthRate);
        m.healthCap = clampNonNegative(prefix + ".healthCap", m.healthCap);
        m.damageRate = clampNonNegative(prefix + ".damageRate", m.damageRate);
        m.damageCap = clampNonNegative(prefix + ".damageCap", m.damageCap);
        m.speedRate = clampNonNegative(prefix + ".speedRate", m.speedRate);
        m.speedCap = clampNonNegative(prefix + ".speedCap", m.speedCap);
        m.followRangeRate = clampNonNegative(prefix + ".followRangeRate", m.followRangeRate);
        m.followRangeCap = clampNonNegative(prefix + ".followRangeCap", m.followRangeCap);
        m.armorRate = clampNonNegative(prefix + ".armorRate", m.armorRate);
        m.armorCap = clampNonNegative(prefix + ".armorCap", m.armorCap);
        m.toughnessRate = clampNonNegative(prefix + ".toughnessRate", m.toughnessRate);
        m.toughnessCap = clampNonNegative(prefix + ".toughnessCap", m.toughnessCap);
    }

    private static double clampNonNegative(String name, double value) {
        if (value < 0) {
            Tribulation.LOGGER.warn("{} must be >= 0, got {}; clamped to 0", name, value);
            return 0;
        }
        return value;
    }

    private static double clampPositive(String name, double value) {
        if (value <= 0) {
            Tribulation.LOGGER.warn("{} must be > 0, got {}; clamped to 1", name, value);
            return 1;
        }
        return value;
    }

    private static double clampUnit(String name, double value) {
        if (value < 0) {
            Tribulation.LOGGER.warn("{} must be in [0,1], got {}; clamped to 0", name, value);
            return 0;
        }
        if (value > 1) {
            Tribulation.LOGGER.warn("{} must be in [0,1], got {}; clamped to 1", name, value);
            return 1;
        }
        return value;
    }

    private static double clampAtLeastOne(String name, double value) {
        // GSON's lenient reader lets NaN through, and NaN slips past every
        // comparison-based clamp — reset it to the floor before it can reach
        // an attribute modifier.
        if (!Double.isFinite(value) || value < 1.0) {
            Tribulation.LOGGER.warn("{} must be >= 1.0, got {}; clamped to 1.0", name, value);
            return 1.0;
        }
        return value;
    }

    private static int clampAtLeast(String name, int value, int min) {
        if (value < min) {
            Tribulation.LOGGER.warn("{} must be >= {}, got {}; clamped to {}", name, min, value, min);
            return min;
        }
        return value;
    }

    private static int clampIntRange(String name, int value, int min, int max) {
        if (value < min) {
            Tribulation.LOGGER.warn("{} must be in [{},{}], got {}; clamped to {}", name, min, max, value, min);
            return min;
        }
        if (value > max) {
            Tribulation.LOGGER.warn("{} must be in [{},{}], got {}; clamped to {}", name, min, max, value, max);
            return max;
        }
        return value;
    }

    private static int clampPercent(String name, int value) {
        if (value < 0) {
            Tribulation.LOGGER.warn("{} must be in [0,100], got {}; clamped to 0", name, value);
            return 0;
        }
        if (value > 100) {
            Tribulation.LOGGER.warn("{} must be in [0,100], got {}; clamped to 100", name, value);
            return 100;
        }
        return value;
    }

    private static Map<String, MobScaling> defaultScaling() {
        Map<String, MobScaling> map = new LinkedHashMap<>();
        // Zombie — reference mob from DESIGN.md.
        map.put("zombie",           MobScaling.of(0.010, 2.50, 0.015, 3.75, 0.0012, 0.30, 0.010, 1.0, 0.032, 8, 0.024, 6));
        // Skeleton — ranged primary; lower melee/armor than zombie.
        map.put("skeleton",         MobScaling.of(0.010, 2.50, 0.012, 3.00, 0.0012, 0.30, 0.010, 1.0, 0.020, 5, 0.015, 4));
        // Creeper — low HP pool; explosion handled separately via mixin.
        map.put("creeper",          MobScaling.of(0.008, 2.00, 0.010, 2.50, 0.0012, 0.30, 0.010, 1.0, 0.016, 4, 0.012, 3));
        // Spider — faster speed scaling than other mobs.
        map.put("spider",           MobScaling.of(0.008, 2.00, 0.012, 3.00, 0.0020, 0.50, 0.010, 1.0, 0.016, 4, 0.012, 3));
        // Cave Spider — smaller, weaker spider variant.
        map.put("cave_spider",      MobScaling.of(0.006, 1.50, 0.010, 2.50, 0.0020, 0.50, 0.010, 1.0, 0.008, 2, 0.008, 2));
        // Endermite — tiny HP, highest speed scaling; no armor.
        map.put("endermite",        MobScaling.of(0.005, 1.25, 0.010, 2.50, 0.0024, 0.60, 0.008, 0.8, 0.0,   0, 0.0,   0));
        // Silverfish — minimal stat scaling, ability-focused.
        map.put("silverfish",       MobScaling.of(0.005, 1.25, 0.008, 2.00, 0.0010, 0.25, 0.008, 0.8, 0.0,   0, 0.0,   0));
        // Drowned — slightly softer than zombie; trident damage via tier abilities.
        map.put("drowned",          MobScaling.of(0.010, 2.50, 0.014, 3.50, 0.0012, 0.30, 0.010, 1.0, 0.024, 6, 0.020, 5));
        // Husk — tougher desert zombie.
        map.put("husk",             MobScaling.of(0.011, 2.75, 0.015, 3.75, 0.0012, 0.30, 0.010, 1.0, 0.032, 8, 0.024, 6));
        // Stray — ice skeleton; mirrors skeleton.
        map.put("stray",            MobScaling.of(0.010, 2.50, 0.012, 3.00, 0.0012, 0.30, 0.010, 1.0, 0.020, 5, 0.015, 4));
        // Pillager — crossbow-based ranged damage.
        map.put("pillager",         MobScaling.of(0.010, 2.50, 0.014, 3.50, 0.0012, 0.30, 0.010, 1.0, 0.024, 6, 0.020, 5));
        // Vindicator — heavy axe melee, tanky.
        map.put("vindicator",       MobScaling.of(0.012, 3.00, 0.020, 5.00, 0.0012, 0.30, 0.010, 1.0, 0.032, 8, 0.024, 6));
        // Witch — minimal direct stats; potions scale via tier abilities.
        map.put("witch",            MobScaling.of(0.008, 2.00, 0.008, 2.00, 0.0008, 0.20, 0.008, 0.8, 0.016, 4, 0.012, 3));
        // Wither Skeleton — tougher nether skeleton with heavy melee.
        map.put("wither_skeleton",  MobScaling.of(0.012, 3.00, 0.016, 4.00, 0.0012, 0.30, 0.010, 1.0, 0.024, 6, 0.020, 5));
        // Guardian — beam-based; slower, medium armor.
        map.put("guardian",         MobScaling.of(0.010, 2.50, 0.014, 3.50, 0.0010, 0.25, 0.010, 1.0, 0.020, 5, 0.020, 5));
        // Hoglin — high health and damage.
        map.put("hoglin",           MobScaling.of(0.014, 3.50, 0.016, 4.00, 0.0012, 0.30, 0.010, 1.0, 0.024, 6, 0.020, 5));
        // Zoglin — similar to hoglin, slightly less damage.
        map.put("zoglin",           MobScaling.of(0.014, 3.50, 0.015, 3.75, 0.0012, 0.30, 0.010, 1.0, 0.024, 6, 0.020, 5));
        // Ravager — extreme tank; slower movement; roar via tier abilities.
        map.put("ravager",          MobScaling.of(0.016, 4.00, 0.014, 3.50, 0.0008, 0.20, 0.010, 1.0, 0.032, 8, 0.024, 6));
        // Piglin — crossbow + melee hybrid.
        map.put("piglin",           MobScaling.of(0.010, 2.50, 0.014, 3.50, 0.0012, 0.30, 0.010, 1.0, 0.024, 6, 0.020, 5));
        // Zombified Piglin — zombie family; group aggro via tier abilities.
        map.put("zombified_piglin", MobScaling.of(0.010, 2.50, 0.015, 3.75, 0.0012, 0.30, 0.010, 1.0, 0.024, 6, 0.020, 5));
        // Bogged — poison skeleton; mirrors skeleton.
        map.put("bogged",           MobScaling.of(0.010, 2.50, 0.012, 3.00, 0.0012, 0.30, 0.010, 1.0, 0.020, 5, 0.015, 4));
        return map;
    }

    private static Map<String, Boolean> defaultMobToggles() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (String mob : MOB_KEYS) {
            map.put(mob, true);
        }
        return map;
    }

    /**
     * Baseline threat offsets per dimension. The Nether and End only receive the
     * time axis by default (distance/height are Overworld-only), so a flat level
     * offset restores the intended difficulty escalation in those dimensions.
     * Keys are dimension {@link ResourceLocation} strings; modded dimensions can
     * be added here too.
     */
    private static Map<String, Integer> defaultDimensionOffsets() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("minecraft:the_nether", 25);
        map.put("minecraft:the_end", 40);
        return map;
    }

    /**
     * Baseline threat offsets per biome, stacking additively with the
     * dimension offset. Keys are biome {@link ResourceLocation} strings or
     * {@code #}-prefixed biome tags (so modded biomes and whole categories
     * work). Only the Deep Dark carries a default — everything else is
     * opt-in tuning.
     */
    private static Map<String, Integer> defaultBiomeOffsets() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("minecraft:deep_dark", 30);
        return map;
    }

    /** A biome offset key is a biome ID or a {@code #}-prefixed biome tag. */
    static boolean isValidBiomeOffsetKey(String key) {
        return isValidIdOrTagKey(key);
    }

    /** A registry-keyed offset key is a plain resource ID or a {@code #}-prefixed tag. */
    static boolean isValidIdOrTagKey(String key) {
        if (key == null || key.isEmpty()) return false;
        String id = key.startsWith("#") ? key.substring(1) : key;
        return !id.isEmpty() && ResourceLocation.tryParse(id) != null;
    }

    /**
     * Danger-zone level boosts for lootable structures. Mobs spawning inside
     * (or within {@code marginBlocks} of) a configured structure's bounding
     * box use an effective level raised by the mapped boost, applied upstream
     * of the scaling axes exactly like the dimension and biome offsets — so
     * stats, tier-ability rolls, and bonus XP all rise together. Keys in
     * {@code boosts} are structure IDs ({@code minecraft:fortress}) or
     * {@code #}-prefixed structure tags. An empty map disables the feature
     * (and its spawn-path lookups) entirely.
     */
    public static class StructureBoosts {
        public static final int MAX_MARGIN_BLOCKS = 128;

        public int marginBlocks = 16;
        public Map<String, Integer> boosts = defaultBoosts();

        /**
         * Baseline boosts for the classic loot structures — the places where
         * reward concentrates and risk should track it. Everything else is
         * opt-in tuning.
         */
        static Map<String, Integer> defaultBoosts() {
            Map<String, Integer> map = new LinkedHashMap<>();
            map.put("minecraft:fortress", 20);
            map.put("minecraft:bastion_remnant", 20);
            map.put("minecraft:monument", 15);
            map.put("minecraft:trial_chambers", 15);
            map.put("minecraft:ancient_city", 30);
            map.put("minecraft:end_city", 25);
            return map;
        }
    }

    public static class General {
        public int maxLevel = 250;
        public int levelUpTicks = 72000;
        public double mobDetectionRange = 32.0;
        public ScalingMode scalingMode = ScalingMode.NEAREST;
        public List<String> excludedEntities = new ArrayList<>(List.of(
                "the_bumblezone:cosmic_crystal_entity"
        ));
        public boolean notifyLevelUp = true;
        public boolean notifyLevelUpShowTier = true;
    }

    public static class TimeScaling {
        public boolean enabled = true;
    }

    public static class DistanceScaling {
        public boolean enabled = true;
        public double startingDistance = 1000;
        public double increasingDistance = 300;
        public double distanceFactor = 0.1;
        public double maxDistanceFactor = 1.5;
        public boolean excludeInOtherDimensions = true;
    }

    public static class HeightScaling {
        public boolean enabled = true;
        public double startingHeight = 62;
        public double heightDistance = 30;
        public double heightFactor = 0.1;
        public double maxHeightFactor = 0.5;
        public boolean positiveHeightScaling = true;
        public boolean negativeHeightScaling = true;
        public boolean excludeInOtherDimensions = true;
    }

    public static class MoonPhaseScaling {
        public boolean enabled = true;
        public double maxBonus = 0.1;
        public boolean surfaceOnly = false;
        public double surfaceY = 63.0;
    }

    /**
     * Rare full-moon event nights. {@code enabled} is the master off-switch for
     * the whole feature; each mechanic underneath has its own knob. The roll
     * happens once per full-moon night at nightfall in the Overworld with
     * probability {@code chance}. While active, the moon scaling axis is
     * multiplied by {@code moonBonusMultiplier}, hostile spawn caps by
     * {@code spawnCapMultiplier}, sleeping is blocked when {@code blockSleep},
     * and clients receive the red-sky tint and nightfall warning sting when
     * {@code clientEffects}. Everything reverts at dawn.
     */
    public static class BloodMoon {
        public boolean enabled = true;
        public double chance = 0.25;
        public double moonBonusMultiplier = 3.0;
        public double spawnCapMultiplier = 2.0;
        public boolean blockSleep = true;
        public boolean clientEffects = true;
    }

    /**
     * Multiplayer group health bonus (off by default). When enabled, each
     * non-spectator player beyond the first within
     * {@link General#mobDetectionRange} of a spawn adds {@code perPlayerBonus}
     * of base max health (0.2 = +20%) to the mob, clipped at {@code maxBonus}.
     * Health only — per-hit damage stays fair for a group — and applied with
     * its own modifier outside the scaling axes, so XP rewards keyed off the
     * health scaling factor don't inflate with group size. A lone player (or
     * a zero {@code maxBonus}) gets no bonus.
     *
     * <p>Scope notes: bosses are excluded (they keep their own gentler
     * {@code BossScalingEngine} formula untouched). The bonus stacks on top
     * of {@code statCaps.maxFactorHealth} — it has its own cap and lives
     * outside the axes, so the axis ceiling is not an absolute health
     * ceiling when this is enabled. Trial-spawner mobs count players by
     * proximity like any other spawn (their level comes from the spawner's
     * detected players); since trial spawners already add mobs per detected
     * player, groups face both more and tougher mobs there — tune
     * {@code perPlayerBonus} down if that compounds too hard.
     */
    public static class GroupHealthBonus {
        public boolean enabled = false;
        public double perPlayerBonus = 0.2;
        public double maxBonus = 1.0;
    }

    public static class StatCaps {
        public double maxFactorHealth = 4.0;
        public double maxFactorDamage = 4.5;
        public double maxFactorSpeed = 0.5;
        public double maxFactorProtection = 2.0;
        public double maxFactorFollowRange = 1.5;
    }

    public static class Totems {
        public boolean countsAsDeathRelief = false;
        public boolean protectsHearts = true;
    }

    public static class DeathRelief {
        public boolean enabled = true;
        public int amount = 2;
        public int cooldownTicks = 6000;
        public int minimumLevel = 0;
    }

    /**
     * Optional level decay for returning players (off by default). No decay
     * accrues until a player has been logged out for {@code graceDays}
     * real-time days; each day beyond the grace window then sheds
     * {@code levelsPerDay} levels, computed once on login from the last
     * disconnect time and floored at {@code floor}. The grace window
     * restarts on every login.
     */
    public static class LevelDecay {
        public boolean enabled = false;
        public double graceDays = 7.0;
        public double levelsPerDay = 2.0;
        public int floor = 0;
    }

    public static class Shards {
        public boolean enabled = true;
        public int dropStartLevel = 25;
        public int shardPower = 5;
        public double dropChance = 0.005;
        public boolean sideEffects = true;
    }

    public static class HardcoreHearts {
        public boolean enabled = false;
        public int heartsLostPerDeath = 2;
        public int minimumHearts = 2;
        public int heartsRestoredPerFragment = 2;
    }

    public static class SoulInventory {
        public boolean enabled = false;
        public String soulboundEnchantment = "tribulation:soulbound";
        public boolean destroyXp = false;
        public boolean respectKeepInventory = true;
    }

    public static class MobScaling {
        public double healthRate = 0.01;
        public double healthCap = 2.5;
        public double damageRate = 0.015;
        public double damageCap = 3.75;
        public double speedRate = 0.0012;
        public double speedCap = 0.3;
        public double followRangeRate = 0.01;
        public double followRangeCap = 1.0;
        public double armorRate = 0.032;
        public double armorCap = 8;
        public double toughnessRate = 0.024;
        public double toughnessCap = 6;

        static MobScaling of(
                double healthRate, double healthCap,
                double damageRate, double damageCap,
                double speedRate, double speedCap,
                double followRangeRate, double followRangeCap,
                double armorRate, double armorCap,
                double toughnessRate, double toughnessCap
        ) {
            MobScaling m = new MobScaling();
            m.healthRate = healthRate;
            m.healthCap = healthCap;
            m.damageRate = damageRate;
            m.damageCap = damageCap;
            m.speedRate = speedRate;
            m.speedCap = speedCap;
            m.followRangeRate = followRangeRate;
            m.followRangeCap = followRangeCap;
            m.armorRate = armorRate;
            m.armorCap = armorCap;
            m.toughnessRate = toughnessRate;
            m.toughnessCap = toughnessCap;
            return m;
        }
    }

    /**
     * Fallback scaling for unlisted hostile mobs — every modded mob that extends
     * {@link Monster} and isn't explicitly handled via the {@code scaling} map.
     * Health + damage only by default so we don't second-guess the mod author's
     * speed/AI/armor tuning.
     */
    public static class UnlistedHostileMobs {
        public boolean enabled = true;
        public List<String> excludedNamespaces = new ArrayList<>();
        public MobScaling scaling = defaultScaling();

        static MobScaling defaultScaling() {
            // Mirrors DESIGN.md: zombie's health/damage rates; other axes zero.
            return MobScaling.of(
                    0.010, 2.50,
                    0.015, 3.75,
                    0.0,   0.0,
                    0.0,   0.0,
                    0.0,   0.0,
                    0.0,   0.0
            );
        }
    }

    public static class SpecialZombies {
        public boolean enabled = true;
        public int bigZombieChance = 10;
        public double bigZombieSize = 1.3;
        public double bigZombieBonusHealth = 10;
        public double bigZombieBonusDamage = 2;
        public double bigZombieSlowness = 0.7;
        public int speedZombieChance = 10;
        public double speedZombieSpeedFactor = 1.3;
        public double speedZombieMalusHealth = 10;
    }

    public static class SpecialSkeletons {
        public boolean enabled = true;
        public int deadeyeSkeletonChance = 10;
        public int deadeyeSkeletonAttackInterval = 20;
        public double deadeyeSkeletonMalusHealth = 10;
        public int bruteSkeletonChance = 10;
        public int bruteSkeletonAttackInterval = 60;
        public double bruteSkeletonBonusHealth = 10;
        public double bruteSkeletonBonusKnockbackResistance = 0.5;
        public double bruteSkeletonSize = 1.3;
    }

    public static class Bosses {
        public boolean affectBosses = true;
        public double bossMaxFactor = 3.0;
        public double bossDistanceFactor = 0.1;
        public double bossTimeFactor = 0.3;
    }

    public static class Xp {
        // Bonus XP gain on a scaled mob's difficulty. Dropped XP becomes
        // base * (1 + healthFactor * xpMultiplier); healthFactor is already
        // bounded by statCaps.maxFactorHealth, so this needs no separate ceiling.
        // 0 disables the bonus (vanilla XP); higher values reward tougher mobs more.
        public double xpMultiplier = 1.0;
    }

    public static class Tiers {
        public int tier1 = 50;
        public int tier2 = 100;
        public int tier3 = 150;
        public int tier4 = 200;
        public int tier5 = 250;
    }

    public static class Hud {
        public boolean enabled = true;
        public Anchor anchor = Anchor.TOP_LEFT;
        public int offsetX = 4;
        public int offsetY = 4;
    }

    public enum Anchor {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    public enum ScalingMode {
        NEAREST, AVERAGE, MAX
    }

    public static class ArmorEquipment {
        public boolean enabled = true;
        public MaterialRollMode materialRollMode = MaterialRollMode.PER_MOB;
        // Tier armor does not drop by default; a loot mod can opt in via the
        // ArmorDropChanceProvider API. [0,2]: >=1.0 requests a guaranteed + pristine drop.
        public double armorDropChance = 0.0;
        public double armorCeiling = 24.0;
        public double toughnessCeiling = 15.0;
        public Map<String, ArmorTier> tiers = defaultArmorTiers();

        public static Map<String, ArmorTier> defaultArmorTiers() {
            Map<String, ArmorTier> map = new LinkedHashMap<>();
            map.put("tier1", new ArmorTier(12, 60, 0, 0, Map.of("leather", 80, "gold", 15, "chain", 5)));
            map.put("tier2", new ArmorTier(18, 68, 10, 1, Map.of("leather", 55, "gold", 25, "chain", 15, "iron", 5)));
            map.put("tier3", new ArmorTier(25, 75, 20, 2, Map.of("leather", 35, "gold", 25, "chain", 20, "iron", 18, "diamond", 2)));
            map.put("tier4", new ArmorTier(35, 80, 30, 2, Map.of("leather", 20, "gold", 18, "chain", 20, "iron", 30, "diamond", 11, "netherite", 1)));
            map.put("tier5", new ArmorTier(45, 85, 40, 3, Map.of("leather", 12, "gold", 12, "chain", 16, "iron", 30, "diamond", 25, "netherite", 5)));
            return map;
        }
    }

    public static class ArmorTier {
        public int wearChancePercent;
        public int slotCoveragePercent;
        public int enchantChancePercent;
        public int maxProtectionLevel;
        public Map<String, Integer> materialWeights;

        public ArmorTier() {}

        public ArmorTier(int wear, int coverage, int enchant, int maxProt, Map<String, Integer> weights) {
            this.wearChancePercent = wear;
            this.slotCoveragePercent = coverage;
            this.enchantChancePercent = enchant;
            this.maxProtectionLevel = maxProt;
            this.materialWeights = new LinkedHashMap<>(weights);
        }
    }

    public static class WeaponEquipment {
        public boolean enabled = true;
        // Weapon drop chance [0,2]: >=1.0 requests guaranteed + pristine drop.
        public double weaponDropChance = 0.0;
        public double damageCeiling = 20.0;
        public Map<String, WeaponTier> tiers = defaultWeaponTiers();

        public static Map<String, WeaponTier> defaultWeaponTiers() {
            Map<String, WeaponTier> map = new LinkedHashMap<>();
            map.put("tier1", new WeaponTier(10, 0, 0, Map.of("wood", 80, "stone", 20)));
            map.put("tier2", new WeaponTier(20, 10, 1, Map.of("wood", 40, "stone", 45, "iron", 15)));
            map.put("tier3", new WeaponTier(30, 20, 2, Map.of("stone", 30, "iron", 60, "diamond", 10)));
            map.put("tier4", new WeaponTier(45, 30, 3, Map.of("iron", 40, "diamond", 55, "netherite", 5)));
            map.put("tier5", new WeaponTier(60, 40, 4, Map.of("iron", 15, "diamond", 65, "netherite", 20)));
            return map;
        }
    }

    public static class WeaponTier {
        public int wearChancePercent;
        public int enchantChancePercent;
        public int maxEnchantmentLevel;
        public Map<String, Integer> materialWeights;

        public WeaponTier() {}

        public WeaponTier(int wear, int enchant, int maxLevel, Map<String, Integer> weights) {
            this.wearChancePercent = wear;
            this.enchantChancePercent = enchant;
            this.maxEnchantmentLevel = maxLevel;
            this.materialWeights = new LinkedHashMap<>(weights);
        }
    }

    public enum MaterialRollMode {
        PER_MOB, PER_SLOT
    }

    /**
     * Elite champion spawns. Above {@code levelThreshold} (effective player
     * level at spawn) a non-boss hostile rolls champion status with
     * probability {@code championChance}. A champion gains 1..{@code maxAffixes}
     * affixes from the pool below, {@code healthMultiplier}/{@code damageMultiplier}
     * on top of normal scaling, a visible name tag and particle aura, boosted
     * XP ({@code xpMultiplier}) and {@code bonusLootRolls} extra rolls of its
     * own loot table on death. {@code enabled} is the master off-switch.
     */
    public static class Champions {
        public boolean enabled = true;
        public int levelThreshold = 50;
        public double championChance = 0.05;
        public int maxAffixes = 2;
        public double healthMultiplier = 1.5;
        public double damageMultiplier = 1.25;
        public double xpMultiplier = 3.0;
        public int bonusLootRolls = 1;
        public boolean showNameTag = true;
        public boolean particleAura = true;
        public Affixes affixes = new Affixes();

        public static class Affixes {
            public boolean vampiric = true;
            public double vampiricHealFraction = 0.5;
            public boolean explosive = true;
            public double explosivePower = 2.0;
            public boolean knockbackAura = true;
            public double knockbackAuraStrength = 0.8;
            public double knockbackAuraRadius = 4.0;
            public int knockbackAuraIntervalTicks = 60;
            public boolean thorns = true;
            public double thornsFraction = 0.3;
            public boolean regenerating = true;
            public double regenHealthPerSecond = 1.0;
        }
    }

    public static class TrialSpawnerConfig {
        public boolean enabled = true;
        public OminousUpgrade ominousUpgrade = new OminousUpgrade();

        public static class OminousUpgrade {
            public boolean enabled = false;
            public float chance = 0.10f;
            public int minimumTier = 3;
        }
    }

    /**
     * Scales raid and pillager-patrol composition with the tier of the
     * targeted player(s). Patrols gain one extra member per
     * {@code patrolBonusRate} tiers; raids at or above
     * {@code extraWaveTierThreshold} run {@code extraWaveCount} additional
     * wave(s). Raider stats/armor/weapons already flow through the normal
     * spawn-scaling path, so this block only governs structural escalation.
     */
    public static class RaidScaling {
        public boolean enabled = true;
        public int patrolBonusRate = 2;
        public int extraWaveTierThreshold = 4;
        public int extraWaveCount = 1;

        /**
         * Extra patrol members for a captain's tier: {@code tier / patrolBonusRate},
         * floored. The {@code patrolBonusRate > 0} guard keeps a rate of 0 from
         * dividing by zero (treated as "no bonus"); {@code enabled = false} is the
         * master off-switch. Pure arithmetic — covered by unit tests.
         */
        public int extraPatrolMembers(int tier) {
            if (!enabled || patrolBonusRate <= 0 || tier <= 0) return 0;
            return tier / patrolBonusRate;
        }

        /**
         * Extra raid waves for the raid's maximum targeted tier:
         * {@code extraWaveCount} when {@code maxTier >= extraWaveTierThreshold},
         * else 0. {@code enabled = false} is the master off-switch. Pure
         * arithmetic — covered by unit tests.
         */
        public int extraWaves(int maxTier) {
            if (!enabled || maxTier < extraWaveTierThreshold) return 0;
            return extraWaveCount;
        }
    }

    /**
     * Tier-gated pack tactics for the classic pack mobs. Above
     * {@code tierThreshold}, hurting an eligible mob alerts same-type mobs
     * within {@code alertRadius} (line-of-sight to the victim required) onto
     * the attacker, and natural spawn groups of eligible types grow by
     * {@code groupSizeBonus}. Below the threshold behavior is fully vanilla;
     * {@code enabled = false} is the master off-switch. {@code eligibleMobs}
     * lists entity type IDs; unknown IDs are logged and ignored.
     */
    public static class PackTactics {
        public static final double MAX_ALERT_RADIUS = 64.0;
        public static final int MAX_GROUP_SIZE_BONUS = 16;

        public boolean enabled = true;
        public int tierThreshold = 3;
        public double alertRadius = 16.0;
        public int groupSizeBonus = 2;
        public List<String> eligibleMobs = defaultEligibleMobs();

        static List<String> defaultEligibleMobs() {
            return new ArrayList<>(List.of(
                    "minecraft:zombie", "minecraft:skeleton", "minecraft:spider"));
        }

        /**
         * Whether pack tactics apply at the given tier. Pure arithmetic —
         * covered by unit tests.
         */
        public boolean isActiveAtTier(int tier) {
            return enabled && tier >= tierThreshold;
        }

        /**
         * Extra members added to a natural spawn group at the given tier:
         * {@code groupSizeBonus} when active, else 0. Pure arithmetic —
         * covered by unit tests.
         */
        public int spawnGroupBonus(int tier) {
            return isActiveAtTier(tier) ? groupSizeBonus : 0;
        }
    }

    /**
     * Tier-gated environmental pressure (off by default): the world itself
     * grows hostile as a player's difficulty climbs. Both effects gate on the
     * <em>player's own</em> stored level (via the tier thresholds), so a
     * low-level player on the same server is unaffected. {@code enabled} is
     * the master off-switch for the whole section; each effect underneath has
     * its own toggle and tier threshold.
     *
     * <p><b>Debilitating strikes</b> — at or above its tier, a landed melee
     * hit from a Tribulation-scaled hostile applies short Weakness and/or
     * Slowness to the player (duration/amplifier configurable per effect).
     * Ranged and environmental damage never trigger it.
     *
     * <p><b>Oppressive nights</b> — at or above its (separate) tier, the dark
     * itself hunts the player: hostiles scaled at night near them spawn with
     * keener senses (a follow-range multiplier, so they notice and pursue
     * from farther away), and night ambient light is subtly reduced for the
     * affected player as the tell. The dimming is client-side: the server
     * syncs a per-player darkness strength; the client bounds it, applies it
     * only at night in daylight-cycle dimensions, and honors both
     * {@code clientEnabled} (read from the client's local config) and the
     * vanilla Darkness Pulsing accessibility slider.
     */
    public static class EnvironmentalPressure {
        public boolean enabled = false;
        public DebilitatingStrikes debilitatingStrikes = new DebilitatingStrikes();
        public OppressiveNights oppressiveNights = new OppressiveNights();

        public static class DebilitatingStrikes {
            public static final int MAX_EFFECT_DURATION_TICKS = 2400;
            public static final int MAX_EFFECT_AMPLIFIER = 4;

            public boolean enabled = true;
            public int tierThreshold = 3;
            public boolean applyWeakness = true;
            public int weaknessDurationTicks = 100;
            public int weaknessAmplifier = 0;
            public boolean applySlowness = false;
            public int slownessDurationTicks = 100;
            public int slownessAmplifier = 0;
        }

        public static class OppressiveNights {
            public static final double MAX_NIGHT_DARKNESS = 0.6;
            public static final double MAX_FOLLOW_RANGE_MULTIPLIER = 3.0;

            public boolean enabled = true;
            public int tierThreshold = 4;
            public double maxDarkness = 0.25;
            /** Client-side opt-out; only ever read from the client's local config. */
            public boolean clientEnabled = true;
            /**
             * Follow-range multiplier for hostiles scaled at night near an
             * affected player. 1.0 disables the mechanical effect (dimming
             * tell only).
             */
            public double followRangeMultiplier = 1.5;
        }

        /**
         * Whether debilitating strikes apply to a player at the given tier.
         * Pure arithmetic — covered by unit tests.
         */
        public boolean strikesActiveAtTier(int tier) {
            return enabled && debilitatingStrikes.enabled && tier >= debilitatingStrikes.tierThreshold;
        }

        /**
         * Oppressive-nights darkness strength for a player at the given tier:
         * {@code maxDarkness} when active, else 0. Pure arithmetic — covered
         * by unit tests.
         */
        public double nightDarknessAtTier(int tier) {
            if (!enabled || !oppressiveNights.enabled) return 0.0;
            if (tier < oppressiveNights.tierThreshold) return 0.0;
            return oppressiveNights.maxDarkness;
        }

        /**
         * Oppressive-nights follow-range multiplier for a mob scaled at night
         * near a player at the given tier: {@code followRangeMultiplier} when
         * active, else 1.0. Pure arithmetic — covered by unit tests.
         */
        public double nightFollowRangeMultiplierAtTier(int tier) {
            if (!enabled || !oppressiveNights.enabled) return 1.0;
            if (tier < oppressiveNights.tierThreshold) return 1.0;
            return oppressiveNights.followRangeMultiplier;
        }
    }

    /**
     * Client-side threat-telegraphing particles. {@code enabled} is the master
     * off-switch; {@code minimumTier} gates the generic high-tier cue on
     * non-variant mobs; {@code particleFrequencyTicks} is the mean interval
     * between emissions (a higher value means rarer particles). Big/Speed
     * zombie variant cues are not gated by {@code minimumTier} — a visibly
     * huge low-tier Big Zombie still telegraphs.
     */
    public static class ThreatParticles {
        public boolean enabled = true;
        public int minimumTier = 4;
        public int particleFrequencyTicks = 40;
    }

    public static class Abilities {
        public boolean zombieReinforcements = true;
        public boolean zombieDoorBreaking = true;
        public boolean zombieSprinting = true;
        public boolean creeperShorterFuse = true;
        public boolean creeperCharged = true;
        public boolean skeletonSwordSwitch = true;
        public boolean skeletonFlameArrows = true;
        public boolean spiderWebPlacing = true;
        public boolean spiderCropTrample = true;
        public boolean spiderLeapAttack = true;
        public boolean huskHunger = true;
        public boolean witherSkeletonSprint = true;
        public boolean witherSkeletonFireAspect = true;
        public boolean drownedTrident = true;
        public boolean hoglinKnockbackResist = true;
        public boolean zoglinFireResist = true;
        public boolean vindicatorResistance = true;
        public boolean zombifiedPiglinAggro = true;
        public boolean piglinCrossbow = true;
        public boolean straySlownessUpgrade = true;
        public boolean boggedPoisonUpgrade = true;
        public boolean witchLingeringPotions = true;
        public boolean witchAggressiveHealing = true;
        public boolean pillagerQuickCharge = true;
        public boolean pillagerMultishot = true;
        public boolean vindicatorDoorBreaking = true;
        public boolean guardianFasterBeam = true;
        public boolean ravagerRoarExpansion = true;
        public boolean silverfishCallSleepers = true;
    }
}
