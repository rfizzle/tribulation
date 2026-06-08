package com.rfizzle.tribulation.data;

import com.rfizzle.tribulation.Tribulation;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class TribulationAttachments {
    public static final AttachmentType<Integer> SCALED_TIER = AttachmentRegistry.createPersistent(
            Tribulation.id("scaled_tier"),
            Codec.INT
    );

    private TribulationAttachments() {}

    public static void register() {
        // No-op to trigger static initialization
    }
}
