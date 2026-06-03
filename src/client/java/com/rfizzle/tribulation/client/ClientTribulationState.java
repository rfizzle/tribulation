package com.rfizzle.tribulation.client;

public final class ClientTribulationState {
    private static int level = 0;
    private static int previousLevel = 0;
    private static long levelUpTimestamp = -1;
    private static int progressTicks = 0;
    private static int goalTicks = 1;

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
            }
        }
    }

    public static long getLevelUpTimestamp() {
        return levelUpTimestamp;
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

    public static void reset() {
        level = 0;
        previousLevel = 0;
        levelUpTimestamp = -1;
        progressTicks = 0;
        goalTicks = 1;
    }
}
