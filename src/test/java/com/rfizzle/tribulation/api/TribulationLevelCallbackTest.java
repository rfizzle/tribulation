package com.rfizzle.tribulation.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * §3.1 isolation lives in the {@code createArrayBacked} invoker: a listener that
 * throws is skipped, the ones registered after it still run, and only a
 * {@link VirtualMachineError} escapes. The event is a static singleton, so every
 * test registers a distinct, self-identifying listener and reads only its own
 * recorder; leftover listeners from earlier tests are inert.
 */
class TribulationLevelCallbackTest {

    @Test
    void aThrowingListenerIsSkippedAndTheNextOneStillRuns() {
        List<String> calls = new ArrayList<>();
        TribulationLevelCallback.EVENT.register((player, oldLevel, newLevel) -> {
            // AbstractMethodError is what a consumer compiled against an older signature
            // surfaces — an Error, which a catch (Exception) would let escape.
            if (oldLevel == 3) throw new AbstractMethodError("stale consumer");
        });
        TribulationLevelCallback.EVENT.register((player, oldLevel, newLevel) -> {
            if (oldLevel == 3) calls.add("after:" + oldLevel + "->" + newLevel);
        });

        TribulationLevelCallback.EVENT.invoker().onLevelChanged(null, 3, 4);

        assertEquals(List.of("after:3->4"), calls,
                "the listener registered after the thrower must still be called");
    }

    @Test
    void aVirtualMachineErrorIsRethrownUnchanged() {
        StackOverflowError jvmIsGone = new StackOverflowError("simulated");
        TribulationLevelCallback.EVENT.register((player, oldLevel, newLevel) -> {
            if (oldLevel == -42) throw jvmIsGone;
        });

        StackOverflowError thrown = assertThrows(StackOverflowError.class,
                () -> TribulationLevelCallback.EVENT.invoker().onLevelChanged(null, -42, 0));
        assertSame(jvmIsGone, thrown, "the VirtualMachineError must escape as-is, not wrapped");
    }
}
