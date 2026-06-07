package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.event.ArmorEquipmentHandler.ArmorMaterial;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.Assertions.*;

class ArmorEquipmentHandlerTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void testRollMaterial_EmptyWeights() {
        assertNull(ArmorEquipmentHandler.rollMaterial(new HashMap<>(), RandomSource.create()));
    }

    @Test
    void testRollMaterial_SingleWeight() {
        Map<String, Integer> weights = Map.of("iron", 100);
        assertEquals(ArmorMaterial.IRON, ArmorEquipmentHandler.rollMaterial(weights, RandomSource.create()));
    }

    @Test
    void testRollMaterial_ZeroWeights() {
        Map<String, Integer> weights = Map.of("iron", 0, "gold", 0);
        assertNull(ArmorEquipmentHandler.rollMaterial(weights, RandomSource.create()));
    }

    @Test
    void testRollMaterial_Distribution() {
        Map<String, Integer> weights = new HashMap<>();
        weights.put("leather", 80);
        weights.put("diamond", 20);

        int leatherCount = 0;
        int diamondCount = 0;
        RandomSource random = RandomSource.create(42);

        for (int i = 0; i < 1000; i++) {
            ArmorMaterial mat = ArmorEquipmentHandler.rollMaterial(weights, random);
            if (mat == ArmorMaterial.LEATHER) leatherCount++;
            else if (mat == ArmorMaterial.DIAMOND) diamondCount++;
        }

        // Expected ~800 leather, ~200 diamond
        assertTrue(leatherCount > 750 && leatherCount < 850, "Leather count out of range: " + leatherCount);
        assertTrue(diamondCount > 150 && diamondCount < 250, "Diamond count out of range: " + diamondCount);
    }

    @Test
    void testRollProtectionLevel() {
        RandomSource random = RandomSource.create(42);

        // max 0 -> 0
        assertEquals(0, ArmorEquipmentHandler.rollProtectionLevel(0, random));

        // max 1 -> 1
        assertEquals(1, ArmorEquipmentHandler.rollProtectionLevel(1, random));

        // distribution check for max 4
        int[] counts = new int[5];
        for (int i = 0; i < 1000; i++) {
            int level = ArmorEquipmentHandler.rollProtectionLevel(4, random);
            assertTrue(level >= 1 && level <= 4);
            counts[level]++;
        }

        // Low bias means level 1 should be more frequent than level 4
        assertTrue(counts[1] > counts[4], "Level 1 (" + counts[1] + ") should be more frequent than level 4 (" + counts[4] + ")");
    }
}
