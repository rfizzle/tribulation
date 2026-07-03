package com.rfizzle.tribulation.sound;

import com.rfizzle.tribulation.Tribulation;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * Custom sound events for Tribulation. {@link #TIER_UP} is the milestone sting
 * played to a player the moment they cross a difficulty tier. The
 * {@link Holder} is handed straight to {@code ClientboundSoundPacket} so the
 * cue can be sent to a single player on a dedicated server.
 */
public final class TribulationSounds {
    public static final ResourceLocation TIER_UP_ID = Tribulation.id("tier_up");
    public static final Holder<SoundEvent> TIER_UP = Registry.registerForHolder(
            BuiltInRegistries.SOUND_EVENT, TIER_UP_ID, SoundEvent.createVariableRangeEvent(TIER_UP_ID));

    /** Ominous nightfall sting sent to Overworld players when a Blood Moon begins. */
    public static final ResourceLocation BLOOD_MOON_WARNING_ID = Tribulation.id("blood_moon_warning");
    public static final Holder<SoundEvent> BLOOD_MOON_WARNING = Registry.registerForHolder(
            BuiltInRegistries.SOUND_EVENT, BLOOD_MOON_WARNING_ID, SoundEvent.createVariableRangeEvent(BLOOD_MOON_WARNING_ID));

    private TribulationSounds() {}

    /** Triggers static initialization so the sound event registers before the registry freezes. */
    public static void register() {
        // No-op: referencing the class forces the static field above to register.
    }
}
