// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigSyncPayloadTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"configVersion\":14,\"shards\":{\"dropStartLevel\":40}}"})
    void roundTrip_preservesJson(String json) {
        ConfigSyncPayload original = new ConfigSyncPayload(json);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ConfigSyncPayload.STREAM_CODEC.encode(buf, original);
        ConfigSyncPayload decoded = ConfigSyncPayload.STREAM_CODEC.decode(buf);
        assertEquals(json, decoded.json());
        buf.release();
    }

    @Test
    void type_hasExpectedId() {
        assertNotNull(ConfigSyncPayload.TYPE);
        assertEquals("tribulation", ConfigSyncPayload.TYPE.id().getNamespace());
        assertEquals("config_sync", ConfigSyncPayload.TYPE.id().getPath());
    }

    @Test
    void payload_type_returnsCorrectType() {
        ConfigSyncPayload payload = new ConfigSyncPayload("{}");
        assertEquals(ConfigSyncPayload.TYPE, payload.type());
    }
}
