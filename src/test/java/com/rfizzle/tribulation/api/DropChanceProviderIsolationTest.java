package com.rfizzle.tribulation.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Both provider slots are §3.1 trust boundaries: a provider that throws — including an
 * {@code Error} from a stale consumer — or returns a non-finite value yields the host's
 * default, and only a {@link VirtualMachineError} escapes.
 */
class DropChanceProviderIsolationTest {

    @AfterEach
    void restoreDefaults() {
        TribulationAPI.setArmorDropChanceProvider((mob, tier, slot, stack, d) -> d);
        TribulationAPI.setWeaponDropChanceProvider((mob, tier, stack, d) -> d);
    }

    @Test
    void armorProviderThrowingAnErrorFallsBackToTheDefault() {
        TribulationAPI.setArmorDropChanceProvider((mob, tier, slot, stack, d) -> {
            throw new NoClassDefFoundError("stale consumer");
        });
        assertEquals(0.085f, TribulationAPI.resolveArmorDropChance(null, 1, null, null, 0.085f));
    }

    @Test
    void weaponProviderThrowingAnErrorFallsBackToTheDefault() {
        TribulationAPI.setWeaponDropChanceProvider((mob, tier, stack, d) -> {
            throw new AbstractMethodError("stale consumer");
        });
        assertEquals(0.3f, TribulationAPI.resolveWeaponDropChance(null, 1, null, 0.3f));
    }

    @Test
    void nonFiniteResultsFallBackToTheDefault() {
        TribulationAPI.setArmorDropChanceProvider((mob, tier, slot, stack, d) -> Float.NaN);
        TribulationAPI.setWeaponDropChanceProvider((mob, tier, stack, d) -> Float.POSITIVE_INFINITY);
        assertEquals(0.1f, TribulationAPI.resolveArmorDropChance(null, 1, null, null, 0.1f));
        assertEquals(0.2f, TribulationAPI.resolveWeaponDropChance(null, 1, null, 0.2f));
    }

    @Test
    void virtualMachineErrorsEscapeBothSlots() {
        TribulationAPI.setArmorDropChanceProvider((mob, tier, slot, stack, d) -> {
            throw new OutOfMemoryError("simulated");
        });
        TribulationAPI.setWeaponDropChanceProvider((mob, tier, stack, d) -> {
            throw new StackOverflowError("simulated");
        });
        assertThrows(OutOfMemoryError.class,
                () -> TribulationAPI.resolveArmorDropChance(null, 1, null, null, 0.1f));
        assertThrows(StackOverflowError.class,
                () -> TribulationAPI.resolveWeaponDropChance(null, 1, null, 0.1f));
    }
}
