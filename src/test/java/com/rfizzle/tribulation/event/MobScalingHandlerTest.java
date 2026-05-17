// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.event;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic tests for the handler's helpers. The event callback itself is
 * integration-tested in-game — it requires a ServerLevel, EntityType registry,
 * and Fabric API wiring that isn't worth mocking.
 */
class MobScalingHandlerTest {

    @Test
    void resolveToggleKey_returnsPathForVanillaMinecraftNamespace() {
        assertEquals("zombie",
                MobScalingHandler.resolveToggleKey(ResourceLocation.fromNamespaceAndPath("minecraft", "zombie")));
        assertEquals("cave_spider",
                MobScalingHandler.resolveToggleKey(ResourceLocation.fromNamespaceAndPath("minecraft", "cave_spider")));
        assertEquals("wither_skeleton",
                MobScalingHandler.resolveToggleKey(ResourceLocation.fromNamespaceAndPath("minecraft", "wither_skeleton")));
    }

    @Test
    void resolveToggleKey_nullForNonMinecraftNamespace() {
        assertNull(MobScalingHandler.resolveToggleKey(
                ResourceLocation.fromNamespaceAndPath("modid", "custommob")));
        assertNull(MobScalingHandler.resolveToggleKey(
                ResourceLocation.fromNamespaceAndPath("the_bumblezone", "cosmic_crystal_entity")));
    }

    @Test
    void resolveToggleKey_nullForNullInput() {
        assertNull(MobScalingHandler.resolveToggleKey(null));
    }

    @Test
    void isExcluded_detectsFullResourceLocation() {
        List<String> excluded = List.of(
                "the_bumblezone:cosmic_crystal_entity",
                "minecraft:armor_stand"
        );
        assertTrue(MobScalingHandler.isExcluded(
                ResourceLocation.fromNamespaceAndPath("the_bumblezone", "cosmic_crystal_entity"),
                excluded));
        assertTrue(MobScalingHandler.isExcluded(
                ResourceLocation.fromNamespaceAndPath("minecraft", "armor_stand"),
                excluded));
        assertFalse(MobScalingHandler.isExcluded(
                ResourceLocation.fromNamespaceAndPath("minecraft", "zombie"),
                excluded));
    }

    @Test
    void isExcluded_handlesEmptyAndNullLists() {
        ResourceLocation zombie = ResourceLocation.fromNamespaceAndPath("minecraft", "zombie");
        assertFalse(MobScalingHandler.isExcluded(zombie, null));
        assertFalse(MobScalingHandler.isExcluded(zombie, List.of()));
        assertFalse(MobScalingHandler.isExcluded(null, List.of("minecraft:zombie")));
    }

    @Test
    void bossesTag_hasCommonNamespaceAndExpectedPath() {
        assertEquals("c", MobScalingHandler.BOSSES_TAG.location().getNamespace());
        assertEquals("bosses", MobScalingHandler.BOSSES_TAG.location().getPath());
    }

    @Test
    void processedTag_isStableStringConstant() {
        assertEquals("tribulation_processed", MobScalingHandler.PROCESSED_TAG);
    }
}
