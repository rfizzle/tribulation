package com.rfizzle.tribulation.data;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.scaling.TierManager;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;

import java.util.function.Function;

public final class TribulationAttachments {
    /**
     * The difficulty tier a mob was scaled to, set server-side by
     * {@link com.rfizzle.tribulation.event.MobScalingHandler}. Persisted to NBT
     * and synced to every client tracking the entity so client-only features
     * (threat-telegraphing particles) can read the tier without a custom packet
     * or a server tick. Entity scoreboard tags are not part of the tracking
     * packet, so this attachment is the only reliable "is this mob scaled?"
     * signal on the client.
     */
    public static final AttachmentType<Integer> SCALED_TIER = AttachmentRegistry.create(
            Tribulation.id("scaled_tier"),
            builder -> builder
                    // Clamp on decode: corrupt or hand-edited entity NBT is untrusted
                    // input, and an out-of-range tier would let AbilityManager apply
                    // every ability. A clamping xmap always succeeds and coerces into
                    // range — unlike Codec.intRange, whose decode error makes Fabric
                    // drop the whole attachment (the mob would read as unscaled).
                    .persistent(Codec.INT.xmap(TierManager::clampTier, Function.identity()))
                    .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
    );

    /**
     * The affix ids of a champion mob, set server-side by
     * {@link com.rfizzle.tribulation.champion.ChampionManager} at spawn.
     * Presence of the attachment is the "is champion" flag. Persisted so a
     * champion survives chunk reload without re-rolling, and synced to
     * tracking clients so the aura particles render without a custom packet.
     */
    public static final AttachmentType<java.util.List<String>> CHAMPION_AFFIXES = AttachmentRegistry.create(
            Tribulation.id("champion_affixes"),
            builder -> builder
                    .persistent(Codec.STRING.listOf())
                    .syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.all())
    );

    private TribulationAttachments() {}

    public static void register() {
        // No-op to trigger static initialization
    }
}
