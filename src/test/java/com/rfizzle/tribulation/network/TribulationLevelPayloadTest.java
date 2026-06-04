// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TribulationLevelPayloadTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 50, 100, 250, Integer.MAX_VALUE})
    void roundTrip_preservesLevel(int level) {
        TribulationLevelPayload original = new TribulationLevelPayload(level, 0, 1);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        TribulationLevelPayload.STREAM_CODEC.encode(buf, original);
        TribulationLevelPayload decoded = TribulationLevelPayload.STREAM_CODEC.decode(buf);
        assertEquals(level, decoded.level());
        buf.release();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 1000, 36000, 72000, Integer.MAX_VALUE})
    void roundTrip_preservesProgressTicks(int progressTicks) {
        TribulationLevelPayload original = new TribulationLevelPayload(0, progressTicks, 72000);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        TribulationLevelPayload.STREAM_CODEC.encode(buf, original);
        TribulationLevelPayload decoded = TribulationLevelPayload.STREAM_CODEC.decode(buf);
        assertEquals(progressTicks, decoded.progressTicks());
        buf.release();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100, 72000, 1728000, Integer.MAX_VALUE})
    void roundTrip_preservesGoalTicks(int goalTicks) {
        TribulationLevelPayload original = new TribulationLevelPayload(0, 0, goalTicks);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        TribulationLevelPayload.STREAM_CODEC.encode(buf, original);
        TribulationLevelPayload decoded = TribulationLevelPayload.STREAM_CODEC.decode(buf);
        assertEquals(goalTicks, decoded.goalTicks());
        buf.release();
    }

    @Test
    void type_isNotNull() {
        assertNotNull(TribulationLevelPayload.TYPE);
        assertNotNull(TribulationLevelPayload.TYPE.id());
        assertEquals("tribulation", TribulationLevelPayload.TYPE.id().getNamespace());
        assertEquals("level_sync", TribulationLevelPayload.TYPE.id().getPath());
    }

    @Test
    void payload_recordAccessors_work() {
        TribulationLevelPayload payload = new TribulationLevelPayload(42, 36000, 72000);
        assertEquals(42, payload.level());
        assertEquals(36000, payload.progressTicks());
        assertEquals(72000, payload.goalTicks());
    }

    @Test
    void payload_type_returnsCorrectType() {
        TribulationLevelPayload payload = new TribulationLevelPayload(0, 0, 1);
        assertEquals(TribulationLevelPayload.TYPE, payload.type());
    }
}
