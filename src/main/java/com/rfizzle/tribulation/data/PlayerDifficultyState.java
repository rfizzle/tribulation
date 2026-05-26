package com.rfizzle.tribulation.data;

import com.rfizzle.tribulation.Tribulation;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerDifficultyState extends SavedData {
    public static final String STORAGE_KEY = "tribulation_players";
    public static final long NEVER_DIED = Long.MIN_VALUE;

    private static final String NBT_PLAYERS_KEY = "Players";
    private static final String NBT_UUID_KEY = "UUID";
    private static final String NBT_LEVEL_KEY = "Level";
    private static final String NBT_TICK_KEY = "Tick";
    private static final String NBT_LAST_DEATH_TICK_KEY = "LastDeathTick";
    private static final String NBT_HEARTS_LOST_KEY = "HeartsLost";

    public static final SavedData.Factory<PlayerDifficultyState> FACTORY = new SavedData.Factory<>(
            PlayerDifficultyState::new,
            PlayerDifficultyState::load,
            null
    );

    private final Map<UUID, PlayerData> data = new LinkedHashMap<>();

    public PlayerDifficultyState() {}

    public static PlayerDifficultyState getOrCreate(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        DimensionDataStorage storage = overworld.getDataStorage();
        return storage.computeIfAbsent(FACTORY, STORAGE_KEY);
    }

    public PlayerData getPlayerData(UUID uuid) {
        return data.computeIfAbsent(uuid, k -> new PlayerData());
    }

    public int getLevel(UUID uuid) {
        return getPlayerData(uuid).level;
    }

    public Set<UUID> trackedPlayers() {
        return Collections.unmodifiableSet(data.keySet());
    }

    /**
     * Advance the player's tick counter by {@code amount}. Returns the number
     * of difficulty levels gained by this call (0 when the counter merely
     * accumulated or when the player is already at {@code maxLevel}). The
     * persistent-state dirty flag is raised whenever any field mutates — even
     * if no level boundary was crossed (e.g. the counter zeroes while the
     * player is capped at max level).
     */
    public int incrementTick(UUID uuid, int amount, int levelUpTicks, int maxLevel) {
        if (amount <= 0 || levelUpTicks <= 0 || maxLevel <= 0) {
            return 0;
        }
        PlayerData pd = getPlayerData(uuid);
        int oldLevel = pd.level;
        int oldTick = pd.tickCounter;
        int levelsGained = applyTicks(pd, amount, levelUpTicks, maxLevel);
        if (pd.level != oldLevel || pd.tickCounter != oldTick) {
            setDirty();
        }
        return levelsGained;
    }

    /**
     * Apply death relief to the given player. Returns {@code true} when the
     * death was off cooldown (even if the level was already at the floor), or
     * {@code false} when the death fell inside the cooldown window and no
     * relief was granted. Callers are expected to have already checked the
     * {@code deathRelief.enabled} config flag.
     */
    public boolean reduceLevel(UUID uuid, int amount, int cooldownTicks, int minimumLevel, long currentTick) {
        PlayerData pd = getPlayerData(uuid);
        Reduction result = applyReduce(pd, amount, cooldownTicks, minimumLevel, currentTick);
        if (result != Reduction.ON_COOLDOWN) {
            setDirty();
        }
        return result != Reduction.ON_COOLDOWN;
    }

    /**
     * Package-private helper exposed for unit tests. Applies death relief to a
     * raw {@link PlayerData} without touching {@link #setDirty()}.
     */
    static Reduction applyReduce(PlayerData pd, int amount, int cooldownTicks, int minimumLevel, long currentTick) {
        int clampedAmount = Math.max(0, amount);
        int floor = Math.max(0, minimumLevel);

        // Treat NEVER_DIED as "always off cooldown" so we do not underflow when
        // computing currentTick - Long.MIN_VALUE. A zero cooldown also means
        // every death qualifies, matching DESIGN.md's "rapid suicide" gate off.
        if (pd.lastDeathTick != NEVER_DIED && cooldownTicks > 0) {
            long elapsed = currentTick - pd.lastDeathTick;
            if (elapsed < cooldownTicks) {
                return Reduction.ON_COOLDOWN;
            }
        }

        int newLevel = Math.max(floor, pd.level - clampedAmount);
        boolean levelChanged = newLevel != pd.level;
        pd.level = newLevel;
        pd.lastDeathTick = currentTick;
        return levelChanged ? Reduction.APPLIED : Reduction.APPLIED_AT_FLOOR;
    }

    enum Reduction {
        APPLIED,
        APPLIED_AT_FLOOR,
        ON_COOLDOWN
    }

    /**
     * Set the player's level directly and reset the tick counter. Clamped to
     * {@code [0, maxLevel]}. Returns the level that was actually stored after
     * clamping. Used by {@code /tribulation set}.
     */
    public int setLevel(UUID uuid, int newLevel, int maxLevel) {
        int clampedMax = Math.max(1, maxLevel);
        int clamped = Math.max(0, Math.min(newLevel, clampedMax));
        PlayerData pd = getPlayerData(uuid);
        if (pd.level != clamped || pd.tickCounter != 0) {
            pd.level = clamped;
            pd.tickCounter = 0;
            setDirty();
        }
        return clamped;
    }

    /**
     * Reset the player's level and tick counter to zero. Used by
     * {@code /tribulation reset}. Does not touch the death-relief
     * cooldown timer — resetting level shouldn't re-open a fresh exploit
     * window.
     */
    public void reset(UUID uuid) {
        PlayerData pd = getPlayerData(uuid);
        if (pd.level != 0 || pd.tickCounter != 0) {
            pd.level = 0;
            pd.tickCounter = 0;
            setDirty();
        }
    }

    public int getTickCounter(UUID uuid) {
        return getPlayerData(uuid).tickCounter;
    }

    /**
     * Reduce a player's level by {@code amount}, floored at {@code minimumLevel}.
     * Unlike {@link #reduceLevel}, there is no cooldown check and the
     * death-relief timer is not touched — this path is used by voluntary
     * reductions such as shatter shards. Returns the new level.
     */
    public int reducePlayerLevel(UUID uuid, int amount, int minimumLevel) {
        PlayerData pd = getPlayerData(uuid);
        int clamped = Math.max(0, amount);
        int floor = Math.max(0, minimumLevel);
        int newLevel = Math.max(floor, pd.level - clamped);
        if (newLevel != pd.level) {
            pd.level = newLevel;
            setDirty();
        }
        return newLevel;
    }

    public int getHeartsLost(UUID uuid) {
        return getPlayerData(uuid).heartsLost;
    }

    /**
     * Increment the player's heart penalty by {@code amount} half-hearts,
     * floored so that the player retains at least {@code minimumHearts}
     * half-hearts of max HP (vanilla baseline is 20). Returns the new total
     * {@code heartsLost} value.
     */
    public int addHeartsLost(UUID uuid, int amount, int minimumHearts) {
        if (amount <= 0) return getPlayerData(uuid).heartsLost;
        PlayerData pd = getPlayerData(uuid);
        int maxPenalty = Math.max(0, 20 - Math.max(1, minimumHearts));
        int newHeartsLost = Math.min(maxPenalty, pd.heartsLost + amount);
        if (newHeartsLost != pd.heartsLost) {
            pd.heartsLost = newHeartsLost;
            setDirty();
        }
        return pd.heartsLost;
    }

    /**
     * Restore up to {@code amount} half-hearts, reducing the penalty towards 0.
     * Returns the new {@code heartsLost} value.
     */
    public int restoreHearts(UUID uuid, int amount) {
        if (amount <= 0) return getPlayerData(uuid).heartsLost;
        PlayerData pd = getPlayerData(uuid);
        int newHeartsLost = Math.max(0, pd.heartsLost - amount);
        if (newHeartsLost != pd.heartsLost) {
            pd.heartsLost = newHeartsLost;
            setDirty();
        }
        return pd.heartsLost;
    }

    /**
     * Clear all heart penalties for the player. Returns the previous
     * {@code heartsLost} value.
     */
    public int resetHearts(UUID uuid) {
        PlayerData pd = getPlayerData(uuid);
        int prev = pd.heartsLost;
        if (pd.heartsLost != 0) {
            pd.heartsLost = 0;
            setDirty();
        }
        return prev;
    }

    /**
     * Advance a raw {@link PlayerData} by {@code amount} ticks. Returns the
     * number of level boundaries crossed (0 when only the tick counter moved,
     * including the "zero stale counter at maxLevel" case — that mutates state
     * without gaining a level, so callers must detect dirtiness separately).
     */
    static int applyTicks(PlayerData pd, int amount, int levelUpTicks, int maxLevel) {
        if (pd.level >= maxLevel) {
            // At or above cap — counter should never carry over. Zero it if
            // stale but report zero levels gained.
            pd.tickCounter = 0;
            return 0;
        }
        int oldLevel = pd.level;
        long total = (long) pd.tickCounter + amount;
        long levelsGained = total / levelUpTicks;
        long remaining = total % levelUpTicks;
        long newLevel = (long) pd.level + levelsGained;
        if (newLevel >= maxLevel) {
            pd.level = maxLevel;
            pd.tickCounter = 0;
        } else {
            pd.level = (int) newLevel;
            pd.tickCounter = (int) remaining;
        }
        return pd.level - oldLevel;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, PlayerData> entry : data.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID(NBT_UUID_KEY, entry.getKey());
            playerTag.putInt(NBT_LEVEL_KEY, entry.getValue().level);
            playerTag.putInt(NBT_TICK_KEY, entry.getValue().tickCounter);
            playerTag.putLong(NBT_LAST_DEATH_TICK_KEY, entry.getValue().lastDeathTick);
            playerTag.putInt(NBT_HEARTS_LOST_KEY, entry.getValue().heartsLost);
            list.add(playerTag);
        }
        tag.put(NBT_PLAYERS_KEY, list);
        return tag;
    }

    public static PlayerDifficultyState load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerDifficultyState state = new PlayerDifficultyState();
        if (!tag.contains(NBT_PLAYERS_KEY, Tag.TAG_LIST)) {
            return state;
        }
        ListTag list = tag.getList(NBT_PLAYERS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag playerTag = list.getCompound(i);
            if (!playerTag.contains(NBT_UUID_KEY)) {
                continue;
            }
            UUID uuid;
            try {
                uuid = playerTag.getUUID(NBT_UUID_KEY);
            } catch (Exception e) {
                Tribulation.LOGGER.warn("Skipping malformed player entry in {}", STORAGE_KEY, e);
                continue;
            }
            PlayerData pd = new PlayerData();
            pd.level = Math.max(0, playerTag.getInt(NBT_LEVEL_KEY));
            pd.tickCounter = Math.max(0, playerTag.getInt(NBT_TICK_KEY));
            pd.lastDeathTick = playerTag.contains(NBT_LAST_DEATH_TICK_KEY)
                    ? playerTag.getLong(NBT_LAST_DEATH_TICK_KEY)
                    : NEVER_DIED;
            pd.heartsLost = Math.max(0, playerTag.getInt(NBT_HEARTS_LOST_KEY));
            state.data.put(uuid, pd);
        }
        return state;
    }

    public static class PlayerData {
        public int level;
        public int tickCounter;
        public long lastDeathTick = NEVER_DIED;
        public int heartsLost;

        public PlayerData() {}
    }
}
