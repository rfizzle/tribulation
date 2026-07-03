package com.rfizzle.tribulation.config;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Parsed, immutable view of the {@code biomeOffsets} config map, built once per
 * map generation so the per-spawn lookup does no string parsing or allocation.
 * Plain {@code minecraft:deep_dark}-style keys become exact biome-ID entries;
 * {@code #}-prefixed keys become {@link TagKey} entries so whole biome
 * categories (including modded biomes) can carry one offset. Unparseable keys
 * are skipped here silently — {@link TribulationConfig#validate()} already
 * warned about them at load time.
 *
 * <p>Match semantics: an exact biome-ID entry wins outright; otherwise the
 * largest offset among matching tag entries applies, so overlapping tags
 * behave predictably.
 */
final class BiomeOffsetResolver {
    private final Map<String, Integer> source;
    private final Map<ResourceLocation, Integer> byId;
    private final List<TagEntry> tags;

    private record TagEntry(TagKey<Biome> tag, int offset) {}

    private BiomeOffsetResolver(Map<String, Integer> source,
                                Map<ResourceLocation, Integer> byId,
                                List<TagEntry> tags) {
        this.source = source;
        this.byId = byId;
        this.tags = tags;
    }

    static BiomeOffsetResolver build(Map<String, Integer> biomeOffsets) {
        Map<String, Integer> source = new LinkedHashMap<>(biomeOffsets);
        Map<ResourceLocation, Integer> byId = new LinkedHashMap<>();
        List<TagEntry> tags = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();
            if (key == null || value == null) continue;
            int offset = Math.max(0, value);
            if (key.startsWith("#")) {
                ResourceLocation id = ResourceLocation.tryParse(key.substring(1));
                if (id != null) {
                    tags.add(new TagEntry(TagKey.create(Registries.BIOME, id), offset));
                }
            } else {
                ResourceLocation id = ResourceLocation.tryParse(key);
                if (id != null) {
                    byId.put(id, offset);
                }
            }
        }
        return new BiomeOffsetResolver(source, byId, tags);
    }

    /**
     * True when this resolver was built from a map with the same contents —
     * the staleness check that lets {@link TribulationConfig#getBiomeOffset}
     * rebuild lazily after the map is mutated in place (config reload swaps
     * the whole config object, but tests and hand edits mutate the map).
     */
    boolean matches(Map<String, Integer> biomeOffsets) {
        return source.equals(biomeOffsets);
    }

    int offsetFor(Holder<Biome> biome) {
        if (biome == null) return 0;
        ResourceLocation id = biome.unwrapKey().map(key -> key.location()).orElse(null);
        return offsetFor(id, biome::is);
    }

    /**
     * Pure-logic core of the lookup: exact ID match wins; otherwise the
     * largest offset among matching tags. Takes the tag membership test as a
     * predicate so unit tests can exercise the match semantics without
     * constructing a real {@link Holder}.
     */
    int offsetFor(ResourceLocation biomeId, Predicate<TagKey<Biome>> isInTag) {
        if (biomeId != null) {
            Integer exact = byId.get(biomeId);
            if (exact != null) return exact;
        }
        int best = 0;
        for (int i = 0; i < tags.size(); i++) {
            TagEntry entry = tags.get(i);
            if (entry.offset > best && isInTag.test(entry.tag)) {
                best = entry.offset;
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
