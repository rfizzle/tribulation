package com.rfizzle.tribulation.client;

public final class ClientTribulationState {
    private static int level = 0;
    private static int previousLevel = 0;
    private static long levelUpTimestamp = -1;

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

    public static void reset() {
        level = 0;
        previousLevel = 0;
        levelUpTimestamp = -1;
    }
}
