package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.event.WeaponEquipmentHandler.WeaponMaterial;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WeaponEquipmentHandlerTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void testRollMaterial_EmptyWeights() {
        RandomSource random = RandomSource.create();
        Map<String, Integer> weights = new LinkedHashMap<>();
        assertNull(WeaponEquipmentHandler.rollMaterial(weights, random));
    }

    @Test
    void testRollMaterial_SingleWeight() {
        RandomSource random = RandomSource.create();
        Map<String, Integer> weights = Map.of("iron", 100);
        assertEquals(WeaponMaterial.IRON, WeaponEquipmentHandler.rollMaterial(weights, random));
    }

    @Test
    void testRollMaterial_ZeroWeights() {
        RandomSource random = RandomSource.create();
        Map<String, Integer> weights = Map.of("iron", 0, "stone", 0);
        assertNull(WeaponEquipmentHandler.rollMaterial(weights, random));
    }

    @Test
    void testRollMaterial_WeightedSelection() {
        Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put("wood", 10);
        weights.put("stone", 90);

        // Implementation uses a pure int roll method for testing boundaries in ArmorEquipmentHandler,
        // but WeaponEquipmentHandler only has the RandomSource version currently.
        // Let's check distribution instead to verify the logic.

        int woodCount = 0;
        int stoneCount = 0;
        RandomSource random = RandomSource.create(42);

        for (int i = 0; i < 1000; i++) {
            WeaponMaterial mat = WeaponEquipmentHandler.rollMaterial(weights, random);
            if (mat == WeaponMaterial.WOOD) woodCount++;
            else if (mat == WeaponMaterial.STONE) stoneCount++;
        }

        assertTrue(woodCount > 70 && woodCount < 130, "Wood count out of range: " + woodCount);
        assertTrue(stoneCount > 870 && stoneCount < 930, "Stone count out of range: " + stoneCount);
    }
}
