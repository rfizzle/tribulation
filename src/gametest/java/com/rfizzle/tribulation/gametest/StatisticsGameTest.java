// Tier: 3 (Fabric Gametest)
package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.api.TribulationLevelCallback;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.data.TribulationAttachments;
import com.rfizzle.tribulation.item.TribulationItems;
import com.rfizzle.tribulation.stat.TribulationStats;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

public class StatisticsGameTest implements FabricGameTest {

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void highestLevelReached_isMonotonic(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        // Trigger level change to 15
        TribulationLevelCallback.EVENT.invoker().onLevelChanged(player, 0, 15);
        int statVal1 = player.getStats().getValue(Stats.CUSTOM.get(TribulationStats.HIGHEST_LEVEL_REACHED));
        helper.assertTrue(statVal1 == 15, "stat should be 15, was " + statVal1);

        // Trigger level change to 12 (reduction)
        TribulationLevelCallback.EVENT.invoker().onLevelChanged(player, 15, 12);
        int statVal2 = player.getStats().getValue(Stats.CUSTOM.get(TribulationStats.HIGHEST_LEVEL_REACHED));
        helper.assertTrue(statVal2 == 15, "stat should still be 15, was " + statVal2);

        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void levelsLostToDeathRelief_incrementsByDelta(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(helper.getLevel().getServer());
        state.setLevel(player.getUUID(), 20, Tribulation.getConfig().general.maxLevel);

        // Simulate death relief via direct event call
        com.rfizzle.tribulation.event.DeathReliefHandler.onAfterDeath(player, player.damageSources().generic());

        int statVal = player.getStats().getValue(Stats.CUSTOM.get(TribulationStats.LEVELS_LOST_TO_DEATH_RELIEF));
        helper.assertTrue(statVal > 0, "stat should have increased, was " + statVal);

        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void shardsUsed_incrementsOnConsume(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TribulationItems.SHATTER_SHARD));

        // Use the item
        TribulationItems.SHATTER_SHARD.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        int statVal = player.getStats().getValue(Stats.CUSTOM.get(TribulationStats.SHATTER_SHARDS_USED));
        helper.assertTrue(statVal == 1, "stat should be 1, was " + statVal);

        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void tier5MobsKilled_countsTierFiveOnly(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos pos = new BlockPos(1, 2, 1);

        Mob tier4Mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, pos);
        tier4Mob.setAttached(TribulationAttachments.SCALED_TIER, 4);
        ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(tier4Mob, player.damageSources().playerAttack(player));

        int statVal1 = player.getStats().getValue(Stats.CUSTOM.get(TribulationStats.TIER_5_MOBS_KILLED));
        helper.assertTrue(statVal1 == 0, "tier 4 kill should not count, stat was " + statVal1);

        Mob tier5Mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, pos);
        tier5Mob.setAttached(TribulationAttachments.SCALED_TIER, 5);
        ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(tier5Mob, player.damageSources().playerAttack(player));

        int statVal2 = player.getStats().getValue(Stats.CUSTOM.get(TribulationStats.TIER_5_MOBS_KILLED));
        helper.assertTrue(statVal2 == 1, "tier 5 kill should count, stat was " + statVal2);

        helper.succeed();
    }
}
