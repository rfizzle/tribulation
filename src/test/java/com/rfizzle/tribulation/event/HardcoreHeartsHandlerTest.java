// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests the hardcore hearts logic through PlayerDifficultyState (the handler
 * delegates all state mutations there). Integration with actual ServerPlayer
 * attribute modifiers requires a fabric-loader test or gametest.
 */
class HardcoreHeartsHandlerTest {

    @Test
    void modifierId_isCorrectNamespace() {
        ResourceLocation id = HardcoreHeartsHandler.MODIFIER_ID;
        assertNotNull(id);
        assertEquals("tribulation", id.getNamespace());
        assertEquals("hardcore_hearts", id.getPath());
    }

    @Test
    void deathPenalty_incrementsHeartsLost() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        TribulationConfig.HardcoreHearts cfg = new TribulationConfig.HardcoreHearts();
        cfg.enabled = true;
        cfg.heartsLostPerDeath = 2;
        cfg.minimumHearts = 2;

        int result = state.addHeartsLost(uuid, cfg.heartsLostPerDeath, cfg.minimumHearts);
        assertEquals(2, result);
        assertEquals(2, state.getHeartsLost(uuid));
    }

    @Test
    void deathPenalty_floorsAtMinimumHearts() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        TribulationConfig.HardcoreHearts cfg = new TribulationConfig.HardcoreHearts();
        cfg.enabled = true;
        cfg.heartsLostPerDeath = 2;
        cfg.minimumHearts = 6;

        // minimumHearts=6 → maxPenalty = 20 - 6 = 14
        for (int i = 0; i < 20; i++) {
            state.addHeartsLost(uuid, cfg.heartsLostPerDeath, cfg.minimumHearts);
        }
        assertEquals(14, state.getHeartsLost(uuid));
    }

    @Test
    void deathPenalty_noOpWhenDisabled() {
        TribulationConfig.HardcoreHearts cfg = new TribulationConfig.HardcoreHearts();
        // disabled by default
        assertEquals(false, cfg.enabled);
    }

    @Test
    void deathPenalty_independentOfDeathRelief() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.getPlayerData(uuid).level = 50;

        // Apply death relief
        state.reduceLevel(uuid, 2, 6000, 0, 0L);
        assertEquals(48, state.getLevel(uuid));

        // Apply heart penalty — operates on different field entirely
        state.addHeartsLost(uuid, 2, 2);
        assertEquals(2, state.getHeartsLost(uuid));
        assertEquals(48, state.getLevel(uuid));
    }

    @Test
    void multipleDeath_accumulatesPenalty() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        TribulationConfig.HardcoreHearts cfg = new TribulationConfig.HardcoreHearts();
        cfg.heartsLostPerDeath = 2;
        cfg.minimumHearts = 2;

        state.addHeartsLost(uuid, cfg.heartsLostPerDeath, cfg.minimumHearts);
        state.addHeartsLost(uuid, cfg.heartsLostPerDeath, cfg.minimumHearts);
        state.addHeartsLost(uuid, cfg.heartsLostPerDeath, cfg.minimumHearts);
        assertEquals(6, state.getHeartsLost(uuid));
    }

    @Test
    void modifierAmount_shouldBeNegativeHeartsLost() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.addHeartsLost(uuid, 4, 2);

        int heartsLost = state.getHeartsLost(uuid);
        double expectedModifierAmount = -heartsLost;
        assertEquals(-4.0, expectedModifierAmount);
    }
}
