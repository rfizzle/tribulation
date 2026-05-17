// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.config.TribulationConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the soul inventory config defaults and validation.
 * The actual inventory-clearing + soulbound-stashing logic requires a
 * ServerPlayer with a registry (for enchantment resolution), so those
 * paths are tested via fabric-loader integration tests and gametests.
 */
class SoulInventoryHandlerTest {

    @Test
    void config_defaultIsDisabled() {
        TribulationConfig.SoulInventory cfg = new TribulationConfig.SoulInventory();
        assertFalse(cfg.enabled);
    }

    @Test
    void config_defaultSoulboundEnchantment() {
        TribulationConfig.SoulInventory cfg = new TribulationConfig.SoulInventory();
        assertEquals("tribulation:soulbound", cfg.soulboundEnchantment);
    }

    @Test
    void config_defaultDestroyXpIsFalse() {
        TribulationConfig.SoulInventory cfg = new TribulationConfig.SoulInventory();
        assertFalse(cfg.destroyXp);
    }

    @Test
    void config_defaultRespectKeepInventoryIsTrue() {
        TribulationConfig.SoulInventory cfg = new TribulationConfig.SoulInventory();
        assertTrue(cfg.respectKeepInventory);
    }

    @Test
    void config_acceptsExternalEnchantmentId() {
        TribulationConfig.SoulInventory cfg = new TribulationConfig.SoulInventory();
        cfg.soulboundEnchantment = "meridian:tether";
        assertEquals("meridian:tether", cfg.soulboundEnchantment);
    }

    @Test
    void soulInventory_independentOfHardcoreHearts() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.soulInventory.enabled = true;
        cfg.hardcoreHearts.enabled = false;
        assertTrue(cfg.soulInventory.enabled);
        assertFalse(cfg.hardcoreHearts.enabled);
    }

    @Test
    void soulInventory_configRoundTripsWithFullConfig() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.soulInventory.enabled = true;
        cfg.soulInventory.soulboundEnchantment = "meridian:tether";
        cfg.soulInventory.destroyXp = true;
        cfg.soulInventory.respectKeepInventory = false;

        assertTrue(cfg.soulInventory.enabled);
        assertEquals("meridian:tether", cfg.soulInventory.soulboundEnchantment);
        assertTrue(cfg.soulInventory.destroyXp);
        assertFalse(cfg.soulInventory.respectKeepInventory);
    }
}
