package com.rfizzle.tribulation.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Color math for the Blood Moon client tint. The server is authoritative —
 * {@link ClientTribulationState#isBloodMoonActive()} is only ever true when
 * the server broadcast it (and it already folds in the {@code clientEffects}
 * toggle), so this class just gates on the flag plus "we are rendering the
 * Overworld sky" and applies the blends. Blend math is kept as pure static
 * functions so the color curves are unit-testable.
 */
public final class BloodMoonClientEffects {
    /** Deep blood-red the night sky is pulled toward. */
    private static final double SKY_RED = 0.30;
    private static final double SKY_GREEN = 0.02;
    private static final double SKY_BLUE = 0.02;
    /**
     * How far sky and fog are pulled toward the target color. The fog blend
     * composes with the sky blend: vanilla's clear-air fog path already
     * derives part of its color from the (tinted) sky color, so the fog pass
     * is a gentler top-up rather than a second full pull.
     */
    private static final float SKY_BLEND = 0.65f;
    private static final float FOG_BLEND = 0.3f;
    /** Multipliers applied to the moon's shader color. */
    private static final float MOON_GREEN_FACTOR = 0.25f;
    private static final float MOON_BLUE_FACTOR = 0.2f;

    private BloodMoonClientEffects() {}

    /** True when the given level should render Blood Moon visuals right now. */
    public static boolean isTintActive(ClientLevel level) {
        return ClientTribulationState.isBloodMoonActive()
                && level != null
                && level.dimension() == Level.OVERWORLD;
    }

    public static Vec3 tintSky(Vec3 skyColor) {
        return new Vec3(
                blend(skyColor.x, SKY_RED, SKY_BLEND),
                blend(skyColor.y, SKY_GREEN, SKY_BLEND),
                blend(skyColor.z, SKY_BLUE, SKY_BLEND));
    }

    /** Returns the tinted {@code [red, green, blue]} fog components. */
    public static float[] tintFog(float red, float green, float blue) {
        return new float[] {
                (float) blend(red, SKY_RED, FOG_BLEND),
                (float) blend(green, SKY_GREEN, FOG_BLEND),
                (float) blend(blue, SKY_BLUE, FOG_BLEND)};
    }

    public static float tintMoonGreen(float green) {
        return green * MOON_GREEN_FACTOR;
    }

    public static float tintMoonBlue(float blue) {
        return blue * MOON_BLUE_FACTOR;
    }

    static double blend(double base, double target, float factor) {
        return base + (target - base) * factor;
    }
}
