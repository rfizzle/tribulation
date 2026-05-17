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
        TribulationLevelPayload original = new TribulationLevelPayload(level);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        TribulationLevelPayload.STREAM_CODEC.encode(buf, original);
        TribulationLevelPayload decoded = TribulationLevelPayload.STREAM_CODEC.decode(buf);
        assertEquals(level, decoded.level());
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
    void payload_recordAccessor_works() {
        TribulationLevelPayload payload = new TribulationLevelPayload(42);
        assertEquals(42, payload.level());
    }

    @Test
    void payload_type_returnsCorrectType() {
        TribulationLevelPayload payload = new TribulationLevelPayload(0);
        assertEquals(TribulationLevelPayload.TYPE, payload.type());
    }
}
