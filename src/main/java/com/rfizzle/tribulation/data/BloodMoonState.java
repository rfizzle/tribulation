package com.rfizzle.tribulation.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent Blood Moon event state, stored as overworld {@link SavedData} so a
 * server restart mid-event resumes the night instead of silently ending it.
 * {@code active} is the live event flag; {@code lastRolledDay} records the last
 * in-game day whose nightfall roll already happened, so re-checks within the
 * same night (or after a restart) never re-roll the dice.
 */
public class BloodMoonState extends SavedData {
    public static final String STORAGE_KEY = "tribulation_blood_moon";
    public static final long NEVER_ROLLED = Long.MIN_VALUE;

    private static final String NBT_ACTIVE_KEY = "Active";
    private static final String NBT_LAST_ROLLED_DAY_KEY = "LastRolledDay";

    public static final SavedData.Factory<BloodMoonState> FACTORY = new SavedData.Factory<>(
            BloodMoonState::new,
            BloodMoonState::load,
            null
    );

    private boolean active;
    private long lastRolledDay = NEVER_ROLLED;

    public BloodMoonState() {}

    public static BloodMoonState getOrCreate(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, STORAGE_KEY);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        if (this.active != active) {
            this.active = active;
            setDirty();
        }
    }

    public long getLastRolledDay() {
        return lastRolledDay;
    }

    public void markRolled(long day) {
        if (this.lastRolledDay != day) {
            this.lastRolledDay = day;
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(NBT_ACTIVE_KEY, active);
        tag.putLong(NBT_LAST_ROLLED_DAY_KEY, lastRolledDay);
        return tag;
    }

    public static BloodMoonState load(CompoundTag tag, HolderLookup.Provider registries) {
        BloodMoonState state = new BloodMoonState();
        state.active = tag.getBoolean(NBT_ACTIVE_KEY);
        state.lastRolledDay = tag.contains(NBT_LAST_ROLLED_DAY_KEY)
                ? tag.getLong(NBT_LAST_ROLLED_DAY_KEY)
                : NEVER_ROLLED;
        return state;
    }
}
