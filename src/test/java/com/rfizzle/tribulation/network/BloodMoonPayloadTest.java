// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BloodMoonPayloadTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void roundTrip_preservesActive(boolean active) {
        BloodMoonPayload original = new BloodMoonPayload(active);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        BloodMoonPayload.STREAM_CODEC.encode(buf, original);
        BloodMoonPayload decoded = BloodMoonPayload.STREAM_CODEC.decode(buf);
        assertEquals(active, decoded.active());
        buf.release();
    }

    @Test
    void type_isNotNull() {
        assertNotNull(BloodMoonPayload.TYPE);
        assertNotNull(BloodMoonPayload.TYPE.id());
        assertEquals("tribulation", BloodMoonPayload.TYPE.id().getNamespace());
        assertEquals("blood_moon", BloodMoonPayload.TYPE.id().getPath());
    }

    @Test
    void payload_type_returnsCorrectType() {
        BloodMoonPayload payload = new BloodMoonPayload(true);
        assertEquals(BloodMoonPayload.TYPE, payload.type());
    }
}
