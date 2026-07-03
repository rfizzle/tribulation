package com.rfizzle.tribulation.config;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiomeOffsetResolverTest {

    private static final ResourceLocation DEEP_DARK = ResourceLocation.parse("minecraft:deep_dark");
    private static final ResourceLocation SWAMP = ResourceLocation.parse("minecraft:swamp");

    private static final Predicate<TagKey<Biome>> NO_TAGS = tag -> false;

    private static Predicate<TagKey<Biome>> inTags(String... tagIds) {
        Set<TagKey<Biome>> keys = new java.util.HashSet<>();
        for (String id : tagIds) {
            keys.add(TagKey.create(Registries.BIOME, ResourceLocation.parse(id)));
        }
        return keys::contains;
    }

    @Test
    void build_partitionsIdAndTagEntries() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("minecraft:deep_dark", 30);
        map.put("#c:is_swamp", 10);
        BiomeOffsetResolver resolver = BiomeOffsetResolver.build(map);
        assertEquals(1, resolver.idEntryCount());
        assertEquals(1, resolver.tagEntryCount());
    }

    @Test
    void build_skipsUnparseableEntries() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("not a valid id!!", 30);
        map.put("#also bad!!", 10);
        map.put("minecraft:deep_dark", 30);
        BiomeOffsetResolver resolver = BiomeOffsetResolver.build(map);
        assertEquals(1, resolver.idEntryCount());
        assertEquals(0, resolver.tagEntryCount());
    }

    @Test
    void offsetFor_exactIdMatch() {
        BiomeOffsetResolver resolver = BiomeOffsetResolver.build(Map.of("minecraft:deep_dark", 30));
        assertEquals(30, resolver.offsetFor(DEEP_DARK, NO_TAGS));
        assertEquals(0, resolver.offsetFor(SWAMP, NO_TAGS));
    }

    @Test
    void offsetFor_tagMatch() {
        BiomeOffsetResolver resolver = BiomeOffsetResolver.build(Map.of("#c:is_swamp", 15));
        assertEquals(15, resolver.offsetFor(SWAMP, inTags("c:is_swamp")));
        assertEquals(0, resolver.offsetFor(SWAMP, NO_TAGS));
    }

    @Test
    void offsetFor_exactIdWinsOverTag() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("minecraft:swamp", 5);
        map.put("#c:is_swamp", 40);
        BiomeOffsetResolver resolver = BiomeOffsetResolver.build(map);
        assertEquals(5, resolver.offsetFor(SWAMP, inTags("c:is_swamp")));
    }

    @Test
    void offsetFor_largestMatchingTagWins() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("#c:is_swamp", 10);
        map.put("#c:is_overworld", 25);
        BiomeOffsetResolver resolver = BiomeOffsetResolver.build(map);
        assertEquals(25, resolver.offsetFor(SWAMP, inTags("c:is_swamp", "c:is_overworld")));
    }

    @Test
    void offsetFor_nullBiomeId_stillMatchesTags() {
        BiomeOffsetResolver resolver = BiomeOffsetResolver.build(Map.of("#c:is_swamp", 15));
        assertEquals(15, resolver.offsetFor(null, inTags("c:is_swamp")));
    }

    @Test
    void matches_tracksMapContents() {
        Map<String, Integer> map = new LinkedHashMap<>(Map.of("minecraft:deep_dark", 30));
        BiomeOffsetResolver resolver = BiomeOffsetResolver.build(map);
        assertTrue(resolver.matches(map));
        map.put("minecraft:swamp", 10);
        assertFalse(resolver.matches(map),
                "in-place mutation must invalidate the cached resolver");
    }
}
