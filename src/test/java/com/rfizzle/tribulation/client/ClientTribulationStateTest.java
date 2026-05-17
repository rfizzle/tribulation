// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientTribulationStateTest {

    @BeforeEach
    void setUp() {
        ClientTribulationState.reset();
    }

    @Test
    void initialState_isZero() {
        assertEquals(0, ClientTribulationState.getLevel());
        assertEquals(-1, ClientTribulationState.getLevelUpTimestamp());
    }

    @Test
    void setLevel_updatesLevel() {
        ClientTribulationState.setLevel(42);
        assertEquals(42, ClientTribulationState.getLevel());
    }

    @Test
    void setLevel_increase_setsTimestamp() {
        ClientTribulationState.setLevel(5);
        long ts = ClientTribulationState.getLevelUpTimestamp();
        assertTrue(ts > 0, "timestamp should be set on level increase");
    }

    @Test
    void setLevel_decrease_doesNotSetTimestamp() {
        ClientTribulationState.setLevel(10);
        ClientTribulationState.reset();
        ClientTribulationState.setLevel(10);
        long afterIncrease = ClientTribulationState.getLevelUpTimestamp();

        ClientTribulationState.setLevel(5);
        long afterDecrease = ClientTribulationState.getLevelUpTimestamp();
        assertEquals(afterIncrease, afterDecrease, "timestamp should not change on decrease");
    }

    @Test
    void setLevel_sameValue_noChange() {
        ClientTribulationState.setLevel(7);
        long ts = ClientTribulationState.getLevelUpTimestamp();
        ClientTribulationState.setLevel(7);
        assertEquals(ts, ClientTribulationState.getLevelUpTimestamp());
        assertEquals(7, ClientTribulationState.getLevel());
    }

    @Test
    void reset_clearsState() {
        ClientTribulationState.setLevel(100);
        ClientTribulationState.reset();
        assertEquals(0, ClientTribulationState.getLevel());
        assertEquals(-1, ClientTribulationState.getLevelUpTimestamp());
    }
}
