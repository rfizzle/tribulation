// Tier: 3 (Fabric Gametest)
package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.data.BloodMoonState;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.data.TribulationAttachments;
import com.rfizzle.tribulation.scaling.TierManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
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
 *
 * <p>A separate concern also lives here: {@code scaledTier_clampsOutOfRangeTierOnNbtDecode}
 * round-trips a mob's {@code SCALED_TIER} attachment through entity NBT — a path that
 * never touches {@link DimensionDataStorage} — to prove the attachment codec's
 * clamping {@code xmap} coerces a corrupt tier into range on decode. The pure clamp
 * logic is unit-tested in {@code TierManagerTest}; this proves the codec is wired.
 */
public class SavedDataPersistenceGameTest implements FabricGameTest {

    private static final BlockPos SPAWN = new BlockPos(1, 2, 1);

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

    /**
     * The {@code SCALED_TIER} attachment codec must clamp an out-of-range tier from
     * corrupt entity NBT on decode — a too-large tier would otherwise let
     * {@code AbilityManager} apply every ability. {@code setAttached} writes memory
     * directly (bypassing the codec, exactly the shape of a hand-edited save); the
     * clamp lives on the persistent codec's <em>decode</em>, so the round-trip
     * through NBT is what proves the xmap is wired and fires. Unit tests cover the
     * pure {@link TierManager#clampTier} logic; this proves the wiring.
     */
    @GameTest(template = "tribulation:empty_3x3")
    public void scaledTier_clampsOutOfRangeTierOnNbtDecode(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Mob source = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SPAWN);

        // Too-large tier clamps down to MAX_TIER on decode.
        source.setAttached(TribulationAttachments.SCALED_TIER, 999);
        int reloadedTier = decodeTierThroughNbt(level, source);
        helper.assertValueEqual(reloadedTier, TierManager.MAX_TIER,
                "out-of-range scaled tier clamps to MAX_TIER on NBT decode");

        // Negative tier clamps up to MIN_TIER on decode.
        source.setAttached(TribulationAttachments.SCALED_TIER, -7);
        int reloadedNegTier = decodeTierThroughNbt(level, source);
        helper.assertValueEqual(reloadedNegTier, TierManager.MIN_TIER,
                "negative scaled tier clamps to MIN_TIER on NBT decode");

        helper.succeed();
    }

    /**
     * Encode {@code source}'s attachments to NBT (identity on write) and decode
     * them into a fresh, unadded entity — the codec's decode path a chunk reload
     * runs — returning the clamped {@code SCALED_TIER}.
     */
    private static int decodeTierThroughNbt(ServerLevel level, Mob source) {
        CompoundTag tag = source.saveWithoutId(new CompoundTag());
        Zombie reloaded = EntityType.ZOMBIE.create(level);
        if (reloaded == null) {
            throw new IllegalStateException("could not create a zombie to decode into");
        }
        try {
            reloaded.load(tag);
            return reloaded.getAttachedOrThrow(TribulationAttachments.SCALED_TIER);
        } finally {
            reloaded.discard();
        }
    }
}
