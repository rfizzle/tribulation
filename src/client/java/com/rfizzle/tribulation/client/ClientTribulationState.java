package com.rfizzle.tribulation.client;

public final class ClientTribulationState {
    private static int level = -1;
    private static int previousLevel = -1;
    private static long levelUpTimestamp = -1;
    private static long levelDropTimestamp = -1;
    private static int progressTicks = 0;
    private static int goalTicks = 1;
    private static boolean bloodMoonActive = false;
    private static float oppressiveNightDarkness = 0f;

    private ClientTribulationState() {}

    public static int getLevel() {
        return level;
    }

    public static void setLevel(int newLevel) {
        if (newLevel != level) {
            previousLevel = level;
            level = newLevel;
            if (newLevel > previousLevel) {
                levelUpTimestamp = System.currentTimeMillis();
            } else if (previousLevel >= 0) {
                // Any drop (death relief, decay, shard use) plays the cooling
                // flash. Skip the first sync from -1, which is initial state,
                // not a real decrease.
                levelDropTimestamp = System.currentTimeMillis();
            }
        }
    }

    public static long getLevelUpTimestamp() {
        return levelUpTimestamp;
    }

    public static long getLevelDropTimestamp() {
        return levelDropTimestamp;
    }

    public static int getProgressTicks() {
        return progressTicks;
    }

    public static int getGoalTicks() {
        return goalTicks;
    }

    public static void setProgress(int progressTicks, int goalTicks) {
        ClientTribulationState.progressTicks = Math.max(0, progressTicks);
        ClientTribulationState.goalTicks = Math.max(1, goalTicks);
    }

    public static float getProgressFraction() {
        if (goalTicks <= 0) return 0f;
        float f = (float) progressTicks / (float) goalTicks;
        if (f < 0f) return 0f;
        if (f > 1f) return 1f;
        return f;
    }

    public static boolean isBloodMoonActive() {
        return bloodMoonActive;
    }

    public static void setBloodMoonActive(boolean active) {
        bloodMoonActive = active;
    }

    /**
     * Server-synced oppressive-nights darkness strength for the local player;
     * {@code 0} when the effect does not apply. Presentation rules (night
     * only, dimension, ceiling, opt-out) live in
     * {@code EnvironmentalPressureClientEffects}.
     */
    public static float getOppressiveNightDarkness() {
        return oppressiveNightDarkness;
    }

    public static void setOppressiveNightDarkness(float darkness) {
        // Reject a non-finite synced value (NaN, ±Inf) — it must read as "off",
        // never propagate toward the lightmap. Math.max(0f, NaN) is NaN, so the
        // finite check has to come first.
        oppressiveNightDarkness = Float.isFinite(darkness) ? Math.max(0f, darkness) : 0f;
    }

    public static void reset() {
        level = -1;
        previousLevel = -1;
        levelUpTimestamp = -1;
        levelDropTimestamp = -1;
        progressTicks = 0;
        goalTicks = 1;
        bloodMoonActive = false;
        oppressiveNightDarkness = 0f;
        ClientConfigState.clear();
    }
}
