// Tier: 2 (fabric-loader-junit — needs CompoundTag)
package com.rfizzle.tribulation.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloodMoonStateTest {

    @Test
    void freshState_isInactiveAndNeverRolled() {
        BloodMoonState state = new BloodMoonState();
        assertFalse(state.isActive());
        assertEquals(BloodMoonState.NEVER_ROLLED, state.getLastRolledDay());
    }

    @Test
    void saveLoad_roundTripsActiveAndLastRolledDay() {
        BloodMoonState state = new BloodMoonState();
        state.setActive(true);
        state.markRolled(42);

        CompoundTag tag = state.save(new CompoundTag(), null);
        BloodMoonState loaded = BloodMoonState.load(tag, null);

        assertTrue(loaded.isActive());
        assertEquals(42, loaded.getLastRolledDay());
    }

    @Test
    void load_missingLastRolledDay_defaultsToNeverRolled() {
        // Simulate a tag written before the field existed.
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Active", true);

        BloodMoonState loaded = BloodMoonState.load(tag, null);

        assertTrue(loaded.isActive());
        assertEquals(BloodMoonState.NEVER_ROLLED, loaded.getLastRolledDay());
    }

    @Test
    void setActive_isDirtyOnlyOnChange() {
        BloodMoonState state = new BloodMoonState();
        state.setActive(false);
        assertFalse(state.isDirty(), "no-op setActive must not dirty the state");
        state.setActive(true);
        assertTrue(state.isDirty());
    }
}
