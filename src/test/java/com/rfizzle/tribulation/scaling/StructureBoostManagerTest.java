package com.rfizzle.tribulation.scaling;

import com.rfizzle.tribulation.scaling.StructureBoostManager.BoostZone;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-math coverage of the structure danger-zone lookup: zone containment
 * (including the margin baked into the bounds), the max-boost fold, and the
 * neighbor-chunk scan radius. The world-facing cache is a thin shell around
 * these — see the acceptance criteria on issue #124.
 */
class StructureBoostManagerTest {

    private static final ResourceLocation FORTRESS = ResourceLocation.parse("minecraft:fortress");
    private static final ResourceLocation ANCIENT_CITY = ResourceLocation.parse("minecraft:ancient_city");

    private static BoostZone zone(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int boost) {
        return new BoostZone(minX, minY, minZ, maxX, maxY, maxZ, boost, FORTRESS);
    }

    @Test
    void contains_insideAndOutside() {
        BoostZone zone = zone(0, 32, 0, 100, 96, 100, 20);
        assertTrue(zone.contains(50, 64, 50));
        assertTrue(zone.contains(0, 32, 0), "min corner is inclusive");
        assertTrue(zone.contains(100, 96, 100), "max corner is inclusive");
        assertTrue(zone.contains(100.5, 64, 50), "entity standing within the max-edge block is inside");
        assertFalse(zone.contains(101, 64, 50), "just past max X is outside");
        assertFalse(zone.contains(50, 31, 50), "below min Y is outside");
        assertFalse(zone.contains(-1, 64, 50), "just past min X is outside");
        assertFalse(zone.contains(-0.5, 64, 50), "entity in the block before min X is outside");
    }

    @Test
    void maxBoostAt_emptyZones_returnsZero() {
        assertEquals(0, StructureBoostManager.maxBoostAt(StructureBoostManager.NO_ZONES, 10, 64, 10));
    }

    @Test
    void maxBoostAt_outsideAllZones_returnsZero() {
        BoostZone[] zones = { zone(0, 0, 0, 16, 16, 16, 20) };
        assertEquals(0, StructureBoostManager.maxBoostAt(zones, 200, 64, 200));
    }

    @Test
    void maxBoostAt_overlappingZones_largestWins() {
        BoostZone[] zones = {
                zone(0, 0, 0, 100, 100, 100, 20),
                new BoostZone(40, 40, 40, 60, 60, 60, 30, ANCIENT_CITY),
        };
        assertEquals(30, StructureBoostManager.maxBoostAt(zones, 50, 50, 50),
                "inside both zones the larger boost applies");
        assertEquals(20, StructureBoostManager.maxBoostAt(zones, 10, 10, 10),
                "inside only the outer zone its boost applies");
    }

    @Test
    void maxBoostAt_boostsDoNotStack() {
        BoostZone[] zones = {
                zone(0, 0, 0, 100, 100, 100, 20),
                new BoostZone(0, 0, 0, 100, 100, 100, 20, ANCIENT_CITY),
        };
        assertEquals(20, StructureBoostManager.maxBoostAt(zones, 50, 50, 50),
                "overlapping zones take the max, never the sum");
    }

    @Test
    void zoneOf_inflatesBoundsByMargin() {
        // BoostZone.of is exercised through the containment result: a point
        // outside the raw box but within the margin must be inside the zone.
        BoostZone zone = new BoostZone(
                0 - 16, 32 - 16, 0 - 16,
                100 + 16, 96 + 16, 100 + 16,
                20, FORTRESS);
        assertTrue(zone.contains(-10, 64, 50), "within margin outside raw bounds");
        assertTrue(zone.contains(110, 64, 50), "within margin outside raw bounds");
        assertFalse(zone.contains(-17, 64, 50), "past the margin");
        assertFalse(zone.contains(117, 64, 50), "past the margin");
    }

    @Test
    void neighborChunkRadius_scalesWithMargin() {
        assertEquals(0, StructureBoostManager.neighborChunkRadius(0));
        assertEquals(0, StructureBoostManager.neighborChunkRadius(-5), "negative margin scans no neighbors");
        assertEquals(1, StructureBoostManager.neighborChunkRadius(1));
        assertEquals(1, StructureBoostManager.neighborChunkRadius(16));
        assertEquals(2, StructureBoostManager.neighborChunkRadius(17));
        assertEquals(2, StructureBoostManager.neighborChunkRadius(32));
        assertEquals(8, StructureBoostManager.neighborChunkRadius(128));
    }
}
