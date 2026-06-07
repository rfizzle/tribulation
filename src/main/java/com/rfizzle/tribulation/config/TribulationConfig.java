package com.rfizzle.tribulation.config;

import com.rfizzle.tribulation.Tribulation;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public int configVersion = 2;
    public General general = new General();
    public TimeScaling timeScaling = new TimeScaling();
    public DistanceScaling distanceScaling = new DistanceScaling();
    public HeightScaling heightScaling = new HeightScaling();
    public StatCaps statCaps = new StatCaps();
    public DeathRelief deathRelief = new DeathRelief();
    public Shards shards = new Shards();
    public HardcoreHearts hardcoreHearts = new HardcoreHearts();
    public SoulInventory soulInventory = new SoulInventory();
    public Map<String, MobScaling> scaling = defaultScaling();
    public UnlistedHostileMobs unlistedHostileMobs = new UnlistedHostileMobs();
    public SpecialZombies specialZombies = new SpecialZombies();
    public Bosses bosses = new Bosses();
    public XpAndLoot xpAndLoot = new XpAndLoot();
    public Tiers tiers = new Tiers();
    public Map<String, Boolean> mobToggles = defaultMobToggles();
    public Abilities abilities = new Abilities();
    public ArmorEquipment armorEquipment = new ArmorEquipment();
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
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            Tribulation.LOGGER.error("Failed to save config to {}", path, e);
        }
    }

    public MobScaling getMobScaling(String mobKey) {
        MobScaling result = scaling.get(mobKey);
        return result != null ? result : scaling.getOrDefault("zombie", new MobScaling());
    }

    public boolean isMobEnabled(String mobKey) {
        return mobToggles.getOrDefault(mobKey, Boolean.FALSE);
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
        if (general.excludedEntities == null) general.excludedEntities = new ArrayList<>();
        if (timeScaling == null) timeScaling = new TimeScaling();
        if (distanceScaling == null) distanceScaling = new DistanceScaling();
        if (heightScaling == null) heightScaling = new HeightScaling();
        if (statCaps == null) statCaps = new StatCaps();
        if (deathRelief == null) deathRelief = new DeathRelief();
        if (shards == null) shards = new Shards();
        if (hardcoreHearts == null) hardcoreHearts = new HardcoreHearts();
        if (soulInventory == null) soulInventory = new SoulInventory();
        if (specialZombies == null) specialZombies = new SpecialZombies();
        if (bosses == null) bosses = new Bosses();
        if (xpAndLoot == null) xpAndLoot = new XpAndLoot();
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
        if (hud == null) hud = new Hud();
        if (hud.anchor == null) hud.anchor = Anchor.TOP_LEFT;

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

    private void validate() {
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

        bosses.bossMaxFactor = clampNonNegative("bosses.bossMaxFactor", bosses.bossMaxFactor);
        bosses.bossDistanceFactor = clampNonNegative("bosses.bossDistanceFactor", bosses.bossDistanceFactor);
        bosses.bossTimeFactor = clampNonNegative("bosses.bossTimeFactor", bosses.bossTimeFactor);

        xpAndLoot.maxXpFactor = clampNonNegative("xpAndLoot.maxXpFactor", xpAndLoot.maxXpFactor);
        xpAndLoot.moreLootChance = clampNonNegative("xpAndLoot.moreLootChance", xpAndLoot.moreLootChance);
        xpAndLoot.maxLootChance = clampUnit("xpAndLoot.maxLootChance", xpAndLoot.maxLootChance);

        if (tiers.tier1 < 0) tiers.tier1 = 0;
        if (tiers.tier2 < tiers.tier1) tiers.tier2 = tiers.tier1;
        if (tiers.tier3 < tiers.tier2) tiers.tier3 = tiers.tier2;
        if (tiers.tier4 < tiers.tier3) tiers.tier4 = tiers.tier3;
        if (tiers.tier5 < tiers.tier4) tiers.tier5 = tiers.tier4;

        armorEquipment.armorDropChance = clampNonNegative("armorEquipment.armorDropChance", armorEquipment.armorDropChance);
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

    public static class General {
        public int maxLevel = 250;
        public int levelUpTicks = 72000;
        public double mobDetectionRange = 32.0;
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

    public static class StatCaps {
        public double maxFactorHealth = 4.0;
        public double maxFactorDamage = 4.5;
        public double maxFactorSpeed = 0.5;
        public double maxFactorProtection = 2.0;
        public double maxFactorFollowRange = 1.5;
    }

    public static class DeathRelief {
        public boolean enabled = true;
        public int amount = 2;
        public int cooldownTicks = 6000;
        public int minimumLevel = 0;
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

    public static class Bosses {
        public boolean affectBosses = true;
        public double bossMaxFactor = 3.0;
        public double bossDistanceFactor = 0.1;
        public double bossTimeFactor = 0.3;
    }

    public static class XpAndLoot {
        public boolean extraXp = true;
        public double maxXpFactor = 2.0;
        public boolean dropMoreLoot = false;
        public double moreLootChance = 0.02;
        public double maxLootChance = 0.7;
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

    public static class ArmorEquipment {
        public boolean enabled = true;
        public MaterialRollMode materialRollMode = MaterialRollMode.PER_MOB;
        public double armorDropChance = 0.085;
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

    public enum MaterialRollMode {
        PER_MOB, PER_SLOT
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
    }
}
