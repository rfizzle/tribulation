// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EnvironmentalPressurePayloadTest {

    @ParameterizedTest
    @ValueSource(floats = {0.0f, 0.25f, 0.6f})
    void roundTrip_preservesNightDarkness(float darkness) {
        EnvironmentalPressurePayload original = new EnvironmentalPressurePayload(darkness);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        EnvironmentalPressurePayload.STREAM_CODEC.encode(buf, original);
        EnvironmentalPressurePayload decoded = EnvironmentalPressurePayload.STREAM_CODEC.decode(buf);
        assertEquals(darkness, decoded.nightDarkness());
        buf.release();
    }

    @Test
    void type_isNotNull() {
        assertNotNull(EnvironmentalPressurePayload.TYPE);
        assertNotNull(EnvironmentalPressurePayload.TYPE.id());
        assertEquals("tribulation", EnvironmentalPressurePayload.TYPE.id().getNamespace());
        assertEquals("environmental_pressure", EnvironmentalPressurePayload.TYPE.id().getPath());
    }

    @Test
    void payload_type_returnsCorrectType() {
        EnvironmentalPressurePayload payload = new EnvironmentalPressurePayload(0.25f);
        assertEquals(EnvironmentalPressurePayload.TYPE, payload.type());
    }
}
