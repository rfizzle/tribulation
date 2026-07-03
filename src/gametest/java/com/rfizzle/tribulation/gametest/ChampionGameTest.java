// Tier: 3 (Fabric Gametest)
package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.champion.ChampionManager;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.data.TribulationAttachments;
import com.rfizzle.tribulation.event.MobScalingHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;

import java.util.List;

/**
 * End-to-end coverage of the champion roll inside the spawn-scaling pipeline.
 * Forces the roll deterministic ({@code championChance = 1.0} / threshold
 * gating) so the assertions don't depend on RNG; the roll/selection math
 * itself is covered by {@code ChampionManagerTest}.
 */
public class ChampionGameTest implements FabricGameTest {

    /**
     * Above the threshold with chance 1.0 every hostile spawn becomes a
     * champion: attachment present, champion health modifier applied, custom
     * name visible. Zombie at player level 50 scales to 30 HP; the champion
     * default ×1.5 lifts it to 45 HP.
     */
    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void aboveThreshold_chanceOne_rollsChampion(GameTestHelper helper) {
        TribulationConfig cfg = Tribulation.getConfig();
        boolean savedEnabled = cfg.champions.enabled;
        int savedThreshold = cfg.champions.levelThreshold;
        double savedChance = cfg.champions.championChance;
        double savedHealthMult = cfg.champions.healthMultiplier;
        boolean savedDist = cfg.distanceScaling.enabled;
        boolean savedHeight = cfg.heightScaling.enabled;
        boolean savedMoon = cfg.moonPhaseScaling.enabled;
        boolean savedSpecial = cfg.specialZombies.enabled;
        double savedRange = cfg.general.mobDetectionRange;

        cfg.champions.enabled = true;
        cfg.champions.levelThreshold = 50;
        cfg.champions.championChance = 1.0;
        cfg.champions.healthMultiplier = 1.5;
        cfg.distanceScaling.enabled = false;
        cfg.heightScaling.enabled = false;
        cfg.moonPhaseScaling.enabled = false;
        cfg.specialZombies.enabled = false;
        cfg.general.mobDetectionRange = 2.0;

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos playerAbs = helper.absolutePos(new BlockPos(1, 2, 1));
        player.teleportTo(playerAbs.getX() + 0.5, playerAbs.getY(), playerAbs.getZ() + 0.5);
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(helper.getLevel().getServer());
        state.setLevel(player.getUUID(), 50, cfg.general.maxLevel);

        Zombie zombie;
        try {
            zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        } finally {
            cfg.champions.enabled = savedEnabled;
            cfg.champions.levelThreshold = savedThreshold;
            cfg.champions.championChance = savedChance;
            cfg.champions.healthMultiplier = savedHealthMult;
            cfg.distanceScaling.enabled = savedDist;
            cfg.heightScaling.enabled = savedHeight;
            cfg.moonPhaseScaling.enabled = savedMoon;
            cfg.specialZombies.enabled = savedSpecial;
            cfg.general.mobDetectionRange = savedRange;
            player.discard();
        }

        Zombie z = zombie;
        helper.succeedWhen(() -> {
            helper.assertTrue(z.getTags().contains(MobScalingHandler.PROCESSED_TAG),
                    "scaling handler must have tagged the zombie");
            helper.assertTrue(ChampionManager.isChampion(z),
                    "zombie above threshold with chance 1.0 must be a champion");
            List<String> affixes = z.getAttached(TribulationAttachments.CHAMPION_AFFIXES);
            helper.assertTrue(affixes != null && !affixes.isEmpty() && affixes.size() <= 2,
                    "champion must carry 1..2 affixes");
            helper.assertTrue(
                    z.getAttribute(Attributes.MAX_HEALTH).getModifier(ChampionManager.HEALTH_ID) != null,
                    "champion health modifier must be applied");
            helper.assertValueEqual(z.getMaxHealth(), 45.0f,
                    "level-50 zombie (30 HP) with champion ×1.5");
            helper.assertValueEqual(z.getHealth(), z.getMaxHealth(), "champion spawns topped off");
            helper.assertTrue(z.hasCustomName(), "champion must carry a name tag");
        });
    }

    /**
     * Below the threshold no spawn ever rolls champion, even at chance 1.0 —
     * the acceptance-criteria "below it, never" guarantee.
     */
    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void belowThreshold_neverRollsChampion(GameTestHelper helper) {
        TribulationConfig cfg = Tribulation.getConfig();
        boolean savedEnabled = cfg.champions.enabled;
        int savedThreshold = cfg.champions.levelThreshold;
        double savedChance = cfg.champions.championChance;
        double savedRange = cfg.general.mobDetectionRange;

        cfg.champions.enabled = true;
        cfg.champions.levelThreshold = 100;
        cfg.champions.championChance = 1.0;
        cfg.general.mobDetectionRange = 2.0;

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos playerAbs = helper.absolutePos(new BlockPos(1, 2, 1));
        player.teleportTo(playerAbs.getX() + 0.5, playerAbs.getY(), playerAbs.getZ() + 0.5);
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(helper.getLevel().getServer());
        state.setLevel(player.getUUID(), 50, cfg.general.maxLevel);

        Zombie zombie;
        try {
            zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        } finally {
            cfg.champions.enabled = savedEnabled;
            cfg.champions.levelThreshold = savedThreshold;
            cfg.champions.championChance = savedChance;
            cfg.general.mobDetectionRange = savedRange;
            player.discard();
        }

        Zombie z = zombie;
        helper.succeedWhen(() -> {
            helper.assertTrue(z.getTags().contains(MobScalingHandler.PROCESSED_TAG),
                    "scaling handler must have tagged the zombie");
            helper.assertFalse(ChampionManager.isChampion(z),
                    "zombie below the level threshold must never roll champion");
        });
    }

    /**
     * Feature disabled: no champion, whatever the threshold/chance.
     */
    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void featureDisabled_neverRollsChampion(GameTestHelper helper) {
        TribulationConfig cfg = Tribulation.getConfig();
        boolean savedEnabled = cfg.champions.enabled;
        int savedThreshold = cfg.champions.levelThreshold;
        double savedChance = cfg.champions.championChance;
        double savedRange = cfg.general.mobDetectionRange;

        cfg.champions.enabled = false;
        cfg.champions.levelThreshold = 0;
        cfg.champions.championChance = 1.0;
        cfg.general.mobDetectionRange = 2.0;

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos playerAbs = helper.absolutePos(new BlockPos(1, 2, 1));
        player.teleportTo(playerAbs.getX() + 0.5, playerAbs.getY(), playerAbs.getZ() + 0.5);
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(helper.getLevel().getServer());
        state.setLevel(player.getUUID(), 250, cfg.general.maxLevel);

        Zombie zombie;
        try {
            zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        } finally {
            cfg.champions.enabled = savedEnabled;
            cfg.champions.levelThreshold = savedThreshold;
            cfg.champions.championChance = savedChance;
            cfg.general.mobDetectionRange = savedRange;
            player.discard();
        }

        Zombie z = zombie;
        helper.succeedWhen(() -> helper.assertFalse(ChampionManager.isChampion(z),
                "disabled feature must never roll champion"));
    }
}
