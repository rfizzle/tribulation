package com.rfizzle.tribulation.config;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureBoostResolverTest {

    private static final ResourceLocation FORTRESS = ResourceLocation.parse("minecraft:fortress");
    private static final ResourceLocation ANCIENT_CITY = ResourceLocation.parse("minecraft:ancient_city");

    private static final Predicate<TagKey<Structure>> NO_TAGS = tag -> false;

    private static Predicate<TagKey<Structure>> inTags(String... tagIds) {
        Set<TagKey<Structure>> keys = new java.util.HashSet<>();
        for (String id : tagIds) {
            keys.add(TagKey.create(Registries.STRUCTURE, ResourceLocation.parse(id)));
        }
        return keys::contains;
    }

    @Test
    void build_partitionsIdAndTagEntries() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("minecraft:fortress", 20);
        map.put("#minecraft:village", 10);
        StructureBoostResolver resolver = StructureBoostResolver.build(map);
        assertEquals(1, resolver.idEntryCount());
        assertEquals(1, resolver.tagEntryCount());
    }

    @Test
    void build_skipsUnparseableEntries() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("not a valid id!!", 30);
        map.put("#also bad!!", 10);
        map.put("minecraft:fortress", 20);
        StructureBoostResolver resolver = StructureBoostResolver.build(map);
        assertEquals(1, resolver.idEntryCount());
        assertEquals(0, resolver.tagEntryCount());
    }

    @Test
    void boostFor_exactIdMatch() {
        StructureBoostResolver resolver = StructureBoostResolver.build(Map.of("minecraft:fortress", 20));
        assertEquals(20, resolver.boostFor(FORTRESS, NO_TAGS));
        assertEquals(0, resolver.boostFor(ANCIENT_CITY, NO_TAGS));
    }

    @Test
    void boostFor_tagMatch() {
        StructureBoostResolver resolver = StructureBoostResolver.build(Map.of("#c:dungeons", 15));
        assertEquals(15, resolver.boostFor(ANCIENT_CITY, inTags("c:dungeons")));
        assertEquals(0, resolver.boostFor(ANCIENT_CITY, NO_TAGS));
    }

    @Test
    void boostFor_exactIdWinsOverTag() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("minecraft:fortress", 5);
        map.put("#c:dungeons", 40);
        StructureBoostResolver resolver = StructureBoostResolver.build(map);
        assertEquals(5, resolver.boostFor(FORTRESS, inTags("c:dungeons")));
    }

    @Test
    void boostFor_largestMatchingTagWins() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("#c:dungeons", 10);
        map.put("#c:loot_structures", 25);
        StructureBoostResolver resolver = StructureBoostResolver.build(map);
        assertEquals(25, resolver.boostFor(FORTRESS, inTags("c:dungeons", "c:loot_structures")));
    }

    @Test
    void boostFor_nullStructureId_stillMatchesTags() {
        StructureBoostResolver resolver = StructureBoostResolver.build(Map.of("#c:dungeons", 15));
        assertEquals(15, resolver.boostFor(null, inTags("c:dungeons")));
    }

    @Test
    void matches_tracksMapContents() {
        Map<String, Integer> map = new LinkedHashMap<>(Map.of("minecraft:fortress", 20));
        StructureBoostResolver resolver = StructureBoostResolver.build(map);
        assertTrue(resolver.matches(map));
        map.put("minecraft:ancient_city", 30);
        assertFalse(resolver.matches(map),
                "in-place mutation must invalidate the cached resolver");
    }
}
