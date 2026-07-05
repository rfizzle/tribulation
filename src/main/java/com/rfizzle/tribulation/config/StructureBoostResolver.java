package com.rfizzle.tribulation.config;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Parsed, immutable view of the {@code structureBoosts.boosts} config map,
 * built once per map generation so the per-chunk boost resolution does no
 * string parsing or allocation. Plain {@code minecraft:fortress}-style keys
 * become exact structure-ID entries; {@code #}-prefixed keys become
 * {@link TagKey} entries so whole structure categories (including modded
 * structures) can carry one boost. Unparseable keys are skipped here
 * silently — {@link TribulationConfig#validate()} already warned about them
 * at load time.
 *
 * <p>Match semantics mirror {@link BiomeOffsetResolver}: an exact
 * structure-ID entry wins outright; otherwise the largest boost among
 * matching tag entries applies, so overlapping tags behave predictably.
 */
final class StructureBoostResolver {
    private final Map<String, Integer> source;
    private final Map<ResourceLocation, Integer> byId;
    private final List<TagEntry> tags;

    private record TagEntry(TagKey<Structure> tag, int boost) {}

    private StructureBoostResolver(Map<String, Integer> source,
                                   Map<ResourceLocation, Integer> byId,
                                   List<TagEntry> tags) {
        this.source = source;
        this.byId = byId;
        this.tags = tags;
    }

    static StructureBoostResolver build(Map<String, Integer> boosts) {
        Map<String, Integer> source = new LinkedHashMap<>(boosts);
        Map<ResourceLocation, Integer> byId = new LinkedHashMap<>();
        List<TagEntry> tags = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();
            if (key == null || value == null) continue;
            int boost = Math.max(0, value);
            if (key.startsWith("#")) {
                ResourceLocation id = ResourceLocation.tryParse(key.substring(1));
                if (id != null) {
                    tags.add(new TagEntry(TagKey.create(Registries.STRUCTURE, id), boost));
                }
            } else {
                ResourceLocation id = ResourceLocation.tryParse(key);
                if (id != null) {
                    byId.put(id, boost);
                }
            }
        }
        return new StructureBoostResolver(source, byId, tags);
    }

    /**
     * True when this resolver was built from a map with the same contents —
     * the staleness check that lets {@link TribulationConfig#getStructureBoost}
     * rebuild lazily after the map is mutated in place (config reload swaps
     * the whole config object, but tests and hand edits mutate the map).
     */
    boolean matches(Map<String, Integer> boosts) {
        return source.equals(boosts);
    }

    int boostFor(Holder<Structure> structure) {
        if (structure == null) return 0;
        ResourceLocation id = structure.unwrapKey().map(key -> key.location()).orElse(null);
        return boostFor(id, structure::is);
    }

    /**
     * Pure-logic core of the lookup: exact ID match wins; otherwise the
     * largest boost among matching tags. Takes the tag membership test as a
     * predicate so unit tests can exercise the match semantics without
     * constructing a real {@link Holder}.
     */
    int boostFor(ResourceLocation structureId, Predicate<TagKey<Structure>> isInTag) {
        if (structureId != null) {
            Integer exact = byId.get(structureId);
            if (exact != null) return exact;
        }
        int best = 0;
        for (int i = 0; i < tags.size(); i++) {
            TagEntry entry = tags.get(i);
            if (entry.boost > best && isInTag.test(entry.tag)) {
                best = entry.boost;
            }
        }
        return best;
    }

    int idEntryCount() {
        return byId.size();
    }

    int tagEntryCount() {
        return tags.size();
    }
}
