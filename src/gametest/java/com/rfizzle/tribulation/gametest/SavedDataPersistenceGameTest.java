// Tier: 3 (Fabric Gametest)
package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.data.BloodMoonState;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.util.UUID;

/**
 * End-to-end persistence coverage for the two {@link DimensionDataStorage}-backed
 * states through the real cold-boot read path. {@code PlayerDifficultyStateTest}
 * and {@code BloodMoonStateTest} cover the in-memory {@code save}/{@code load} NBT
 * round-trip, but that path never touches {@link DimensionDataStorage} — the
 * storage layer that runs the datafixer and reads the {@code .dat} off disk. These
 * tests flush the live state to disk, then re-read it through a <em>fresh</em>
 * storage over the same folder (a new {@link DimensionDataStorage} with an empty
 * cache reads from disk exactly as a restarted server does) and assert the values
 * survive.
 *
 * <p>These guard that the factories carry a non-null {@code DataFixTypes}: vanilla
 * {@code DimensionDataStorage} invokes {@code DataFixTypes.update(...)} on read,
 * and while Fabric API's object-builder module currently null-guards that call, a
 * real {@code SAVED_DATA_*} constant is the self-sufficient form these tests
 * exercise end to end.
 */
public class SavedDataPersistenceGameTest implements FabricGameTest {

    /** Fresh storage over the overworld's data folder — the real boot-time read path. */
    private static DimensionDataStorage freshOverworldStorage(MinecraftServer server) {
        File dataDir = server.getWorldPath(LevelResource.ROOT).resolve("data").toFile();
        return new DimensionDataStorage(dataDir, server.getFixerUpper(), server.registryAccess());
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void playerDifficultyState_survivesStorageReload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        // A throwaway UUID so we neither depend on nor disturb any real player.
        UUID uuid = UUID.randomUUID();
        // Stay under any sane maxLevel so setLevel never clamps and the assert
        // is independent of the server's configured cap.
        int target = Math.min(137, Tribulation.getConfig().general.maxLevel);

        PlayerDifficultyState live = PlayerDifficultyState.getOrCreate(server);
        try {
            live.setLevel(uuid, target, Tribulation.getConfig().general.maxLevel);

            // Flush the dirty state to disk, then re-read through a cold storage
            // instance — the datafixer + disk read a restarted server runs at boot.
            server.overworld().getDataStorage().save();
            PlayerDifficultyState reloaded =
                    freshOverworldStorage(server).computeIfAbsent(
                            PlayerDifficultyState.FACTORY, PlayerDifficultyState.STORAGE_KEY);

            helper.assertValueEqual(reloaded.getLevel(uuid), target,
                    "player level survives a disk round-trip");
            helper.succeed();
        } finally {
            // Don't leave a stray entry in the shared live singleton.
            live.reset(uuid);
        }
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void bloodMoonState_survivesStorageReload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();

        BloodMoonState live = BloodMoonState.getOrCreate(server);
        boolean savedActive = live.isActive();
        long savedRolled = live.getLastRolledDay();
        try {
            live.setActive(true);
            live.markRolled(42L);

            server.overworld().getDataStorage().save();
            BloodMoonState reloaded =
                    freshOverworldStorage(server).computeIfAbsent(
                            BloodMoonState.FACTORY, BloodMoonState.STORAGE_KEY);

            helper.assertTrue(reloaded.isActive(),
                    "active Blood Moon survives a disk round-trip");
            helper.assertValueEqual(reloaded.getLastRolledDay(), 42L,
                    "last-rolled day survives a disk round-trip");
            helper.succeed();
        } finally {
            // Restore the shared singleton so other gametests see a clean slate.
            live.setActive(savedActive);
            live.markRolled(savedRolled);
        }
    }
}
