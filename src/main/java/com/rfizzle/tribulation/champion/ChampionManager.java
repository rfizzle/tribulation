package com.rfizzle.tribulation.champion;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.config.TribulationConfig.Champions;
import com.rfizzle.tribulation.data.TribulationAttachments;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.List;

/**
 * Rolls and applies champion status at spawn time. Called from
 * {@code MobScalingHandler.applyScaling} after normal scaling so champion
 * bonuses layer on already-scaled values. The hot-path contract: a mob that
 * does not become a champion costs one threshold comparison and at most one
 * random roll — no allocation.
 *
 * <p>A champion carries the {@link TribulationAttachments#CHAMPION_AFFIXES}
 * attachment (persistent + synced) holding its affix ids; the attachment is
 * both the "is champion" flag and the source of truth for affix behavior in
 * {@code ChampionEffectHandler} and the client aura emitter. Stat bonuses are
 * {@code tribulation:champion_*} attribute modifiers multiplying the final
 * scaled value, so they persist and show up in {@code /tribulation inspect}.
 */
public final class ChampionManager {

    public static final ResourceLocation HEALTH_ID = Tribulation.id("champion_health");
    public static final ResourceLocation DAMAGE_ID = Tribulation.id("champion_damage");

    private ChampionManager() {}

    /**
     * Roll the champion gate and, on success, promote the mob. Returns the
     * applied affixes, or an empty list when the mob stayed normal. The caller
     * guarantees the mob is a non-boss hostile that just went through scaling.
     */
    public static List<ChampionAffix> tryApply(Mob mob, int playerLevel, TribulationConfig cfg, RandomSource random) {
        Champions champions = cfg.champions;
        if (!shouldRoll(playerLevel, champions)) return List.of();
        if (champions.championChance < 1.0 && random.nextDouble() >= champions.championChance) {
            return List.of();
        }

        List<ChampionAffix> pool = enabledAffixes(champions.affixes);
        if (pool.isEmpty()) return List.of();

        List<ChampionAffix> affixes = selectAffixes(pool, champions.maxAffixes, random);
        apply(mob, affixes, champions);
        return affixes;
    }

    /**
     * Pure threshold gate: the feature must be on, the chance positive, and
     * the effective player level at or above {@code levelThreshold}.
     */
    public static boolean shouldRoll(int playerLevel, Champions cfg) {
        if (cfg == null || !cfg.enabled) return false;
        if (cfg.championChance <= 0) return false;
        return playerLevel >= cfg.levelThreshold;
    }

    /** The affix pool with per-affix config toggles applied, in enum order. */
    public static List<ChampionAffix> enabledAffixes(Champions.Affixes cfg) {
        List<ChampionAffix> pool = new ArrayList<>();
        for (ChampionAffix affix : ChampionAffix.values()) {
            if (affix.isEnabled(cfg)) pool.add(affix);
        }
        return pool;
    }

    /**
     * Draw 1..{@code maxAffixes} distinct affixes uniformly from the pool.
     * The count is uniform over [1, maxAffixes], clamped to the pool size.
     */
    public static List<ChampionAffix> selectAffixes(List<ChampionAffix> pool, int maxAffixes, RandomSource random) {
        if (pool.isEmpty()) return List.of();
        int bound = Math.max(1, Math.min(maxAffixes, pool.size()));
        int count = 1 + random.nextInt(bound);
        List<ChampionAffix> remaining = new ArrayList<>(pool);
        List<ChampionAffix> picked = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            picked.add(remaining.remove(random.nextInt(remaining.size())));
        }
        return picked;
    }

    private static void apply(Mob mob, List<ChampionAffix> affixes, Champions cfg) {
        List<String> ids = new ArrayList<>(affixes.size());
        for (ChampionAffix affix : affixes) {
            ids.add(affix.id());
        }
        mob.setAttached(TribulationAttachments.CHAMPION_AFFIXES, List.copyOf(ids));

        // Multiplied-total so the bonus stacks on the fully scaled value,
        // mirroring how the zombie variants layer on the axis modifiers.
        setMultiplier(mob, Attributes.MAX_HEALTH, HEALTH_ID, cfg.healthMultiplier);
        setMultiplier(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, cfg.damageMultiplier);

        if (cfg.showNameTag) {
            mob.setCustomName(championName(affixes, mob.getType().getDescription()));
            mob.setCustomNameVisible(true);
        }
    }

    /**
     * Build the visible tell, e.g. "Vampiric Zombie Champion" or
     * "Vampiric Thorns Zombie Champion" for two affixes.
     */
    public static Component championName(List<ChampionAffix> affixes, Component mobName) {
        if (affixes.size() >= 2) {
            return Component.translatable("champion.tribulation.name.two",
                    Component.translatable(affixes.get(0).translationKey()),
                    Component.translatable(affixes.get(1).translationKey()),
                    mobName);
        }
        return Component.translatable("champion.tribulation.name.one",
                Component.translatable(affixes.get(0).translationKey()),
                mobName);
    }

    /** Whether the mob was promoted to a champion (server or client side). */
    public static boolean isChampion(Mob mob) {
        return mob.hasAttached(TribulationAttachments.CHAMPION_AFFIXES);
    }

    /** The mob's affixes, resolved from the attachment; empty for non-champions. */
    public static List<ChampionAffix> getAffixes(Mob mob) {
        List<String> ids = mob.getAttached(TribulationAttachments.CHAMPION_AFFIXES);
        if (ids == null || ids.isEmpty()) return List.of();
        List<ChampionAffix> affixes = new ArrayList<>(ids.size());
        for (String id : ids) {
            ChampionAffix affix = ChampionAffix.byId(id);
            if (affix != null) affixes.add(affix);
        }
        return affixes;
    }

    public static boolean hasAffix(Mob mob, ChampionAffix affix) {
        List<String> ids = mob.getAttached(TribulationAttachments.CHAMPION_AFFIXES);
        return ids != null && ids.contains(affix.id());
    }

    /**
     * Pure champion XP bonus: multiplies the (already tier-scaled) reward by
     * {@code xpMultiplier} when the mob is a champion. A multiplier at or
     * below 1 disables the bonus.
     */
    public static int applyChampionXp(int xp, boolean isChampion, Champions cfg) {
        if (!isChampion || xp <= 0) return xp;
        if (cfg == null || !cfg.enabled || cfg.xpMultiplier <= 1.0) return xp;
        long scaled = Math.round(xp * cfg.xpMultiplier);
        return scaled > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) scaled;
    }

    private static void setMultiplier(Mob mob, Holder<Attribute> attr, ResourceLocation id, double multiplier) {
        double delta = multiplier - 1.0;
        if (delta == 0) return;
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addPermanentModifier(new AttributeModifier(id, delta, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
}
