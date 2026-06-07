package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.ability.AbilityManager;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.config.TribulationConfig.MobScaling;
import com.rfizzle.tribulation.data.TribulationAttachments;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.scaling.BossScalingEngine;
import com.rfizzle.tribulation.scaling.ScalingEngine;
import com.rfizzle.tribulation.scaling.TierManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Hooks {@link ServerEntityEvents#ENTITY_LOAD} to apply mob scaling at spawn
 * time. Non-{@link Mob} entities, excluded types, bosses, and disabled mob
 * categories are skipped. Each scaled mob is marked with a scoreboard tag so
 * subsequent chunk reloads don't re-process it — this enforces the DESIGN.md
 * rule that a mob's difficulty is frozen at spawn.
 */
public final class MobScalingHandler {
    public static final String PROCESSED_TAG = "tribulation_processed";
    public static final String MINECRAFT_NAMESPACE = "minecraft";

    public static final TagKey<EntityType<?>> BOSSES_TAG = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("c", "bosses")
    );

    private MobScalingHandler() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register(MobScalingHandler::onEntityLoad);
    }

    static void onEntityLoad(Entity entity, ServerLevel world) {
        if (!(entity instanceof Mob mob)) return;
        if (mob.getTags().contains(PROCESSED_TAG)) return;

        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null) return;

        try {
            processMob(mob, world, cfg);
        } catch (Exception e) {
            Tribulation.LOGGER.error("Failed to apply scaling to {}", mob, e);
        }
    }

    private static void processMob(Mob mob, ServerLevel world, TribulationConfig cfg) {
        EntityType<?> type = mob.getType();
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(type);

        if (isExcluded(typeId, cfg.general.excludedEntities)) {
            return;
        }

        // Bosses route through BossScalingEngine: uniform rates for health+damage,
        // time + distance axes only (no height), and distance ignores the
        // excludeInOtherDimensions flag. affectBosses=false skips them entirely.
        if (type.is(BOSSES_TAG)) {
            if (!cfg.bosses.affectBosses) {
                return;
            }
            int bossPlayerLevel = ScalingEngine.getEffectiveLevel(mob, world);
            int bossTier = TierManager.getTier(bossPlayerLevel, cfg.tiers);
            mob.setAttached(TribulationAttachments.SCALED_TIER, bossTier);

            BossScalingEngine.applyModifiers(mob, world, bossPlayerLevel, cfg);
            mob.setHealth(mob.getMaxHealth());
            mob.addTag(PROCESSED_TAG);
            return;
        }

        MobScaling scaling = cfg.resolveScalingForEntity(typeId, mob);
        if (scaling == null) {
            return;
        }

        int playerLevel = ScalingEngine.getEffectiveLevel(mob, world);
        int tier = TierManager.getTier(playerLevel, cfg.tiers);
        mob.setAttached(TribulationAttachments.SCALED_TIER, tier);

        ScalingEngine.applyModifiers(mob, world, playerLevel, cfg, scaling);

        // Abilities and zombie variants are vanilla-only concerns — they key off
        // the mobToggles path and only make sense for the 21 vanilla mob types.
        // Modded mobs scaled via the fallback (or full-ID overrides on non-vanilla
        // namespaces) skip this block; their stats are scaled, but no additional
        // ability injection happens.
        String toggleKey = resolveToggleKey(typeId);
        if (toggleKey != null && cfg.isMobEnabled(toggleKey)) {
            ArmorEquipmentHandler.processArmor(mob, tier, cfg);
            AbilityManager.applyAbilities(mob, tier, toggleKey, cfg);
            ZombieVariantHandler.apply(mob, toggleKey, cfg.specialZombies, world.getRandom());
        }

        ScalingEngine.clampToCeiling(mob, ScalingEngine.ATTR_ARMOR, cfg.armorEquipment.armorCeiling);
        ScalingEngine.clampToCeiling(mob, ScalingEngine.ATTR_TOUGHNESS, cfg.armorEquipment.toughnessCeiling);

        // Modifying max health does not raise current HP; top the mob off so
        // its displayed and effective HP match the scaled maximum at spawn.
        mob.setHealth(mob.getMaxHealth());

        mob.addTag(PROCESSED_TAG);
    }

    /**
     * Derive the {@code mobToggles} config key from a vanilla {@link EntityType}
     * ResourceLocation. Only the {@code minecraft} namespace is supported; modded
     * entities are skipped unless added to the excluded/boss tag systems.
     */
    public static String resolveToggleKey(ResourceLocation typeId) {
        if (typeId == null) return null;
        if (!MINECRAFT_NAMESPACE.equals(typeId.getNamespace())) return null;
        return typeId.getPath();
    }

    public static boolean isExcluded(ResourceLocation typeId, List<String> excludedEntities) {
        if (typeId == null || excludedEntities == null || excludedEntities.isEmpty()) {
            return false;
        }
        String asString = typeId.toString();
        return excludedEntities.contains(asString);
    }

}
