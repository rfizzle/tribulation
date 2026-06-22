// Tier: 3 (Fabric Gametest)
package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.data.TribulationAttachments;
import com.rfizzle.tribulation.item.TribulationItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Verifies every advancement criterion fires from its in-game action by
 * asserting the matching advancement is granted (not merely that the handler
 * ran). Criteria are mod-registered content, so these must be Tier 3 gametests
 * rather than fabric-loader-junit unit tests.
 */
public class AdvancementsGameTest implements FabricGameTest {

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void tierReached_firesCriterion(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TribulationConfig cfg = Tribulation.getConfig();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(helper.getLevel().getServer());

        // One level short of the tier-1 threshold, then add exactly one level.
        state.setLevel(player.getUUID(), cfg.tiers.tier1 - 1, cfg.general.maxLevel);
        Tribulation.applyLevelTick(player, state, cfg, cfg.general.levelUpTicks);

        assertGranted(helper, player, "tier_1");
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void tierUp_grantsLevelingPlayerOnly(GameTestHelper helper) {
        ServerPlayer leveler = helper.makeMockServerPlayerInLevel();
        ServerPlayer bystander = helper.makeMockServerPlayerInLevel();
        TribulationConfig cfg = Tribulation.getConfig();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(helper.getLevel().getServer());

        state.setLevel(leveler.getUUID(), cfg.tiers.tier1 - 1, cfg.general.maxLevel);
        Tribulation.applyLevelTick(leveler, state, cfg, cfg.general.levelUpTicks);

        assertGranted(helper, leveler, "tier_1");
        assertNotGranted(helper, bystander, "tier_1");
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void soulboundSurvived_firesCriterion(GameTestHelper helper) {
        TribulationConfig cfg = Tribulation.getConfig();
        boolean prevEnabled = cfg.soulInventory.enabled;
        boolean prevRespect = cfg.soulInventory.respectKeepInventory;
        cfg.soulInventory.enabled = true;
        cfg.soulInventory.respectKeepInventory = false;
        try {
            ServerPlayer oldPlayer = helper.makeMockServerPlayerInLevel();
            ServerPlayer newPlayer = helper.makeMockServerPlayerInLevel();

            Holder<Enchantment> soulbound = helper.getLevel().getServer().registryAccess()
                    .registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(ResourceKey.create(Registries.ENCHANTMENT,
                            ResourceLocation.parse(cfg.soulInventory.soulboundEnchantment)));
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.enchant(soulbound, 1);
            oldPlayer.getInventory().add(sword);

            com.rfizzle.tribulation.event.SoulInventoryHandler.processDeathInventory(oldPlayer);
            ServerPlayerEvents.COPY_FROM.invoker().copyFromPlayer(oldPlayer, newPlayer, false);

            assertGranted(helper, newPlayer, "soulbound_survived");
            helper.succeed();
        } finally {
            cfg.soulInventory.enabled = prevEnabled;
            cfg.soulInventory.respectKeepInventory = prevRespect;
        }
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void shatterShard_firesCriterion(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TribulationItems.SHATTER_SHARD));

        TribulationItems.SHATTER_SHARD.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        assertGranted(helper, player, "shatter_shard_used");
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void heartFragment_firesCriterion(GameTestHelper helper) {
        TribulationConfig cfg = Tribulation.getConfig();
        boolean prevEnabled = cfg.hardcoreHearts.enabled;
        cfg.hardcoreHearts.enabled = true;
        try {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(helper.getLevel().getServer());
            state.addHeartsLost(player.getUUID(), 2, cfg.hardcoreHearts.minimumHearts);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TribulationItems.HEART_FRAGMENT));

            TribulationItems.HEART_FRAGMENT.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

            assertGranted(helper, player, "heart_fragment_used");
            helper.succeed();
        } finally {
            cfg.hardcoreHearts.enabled = prevEnabled;
        }
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void tierFiveMobKill_firesCriterion(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos pos = new BlockPos(1, 2, 1);

        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, pos);
        mob.setAttached(TribulationAttachments.SCALED_TIER, 5);
        ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(mob, player.damageSources().playerAttack(player));

        assertGranted(helper, player, "tier_five_mob_killed");
        helper.succeed();
    }

    private static void assertGranted(GameTestHelper helper, ServerPlayer player, String path) {
        AdvancementHolder advancement = resolve(helper, player, path);
        helper.assertTrue(player.getAdvancements().getOrStartProgress(advancement).isDone(),
                "advancement " + path + " should be granted");
    }

    private static void assertNotGranted(GameTestHelper helper, ServerPlayer player, String path) {
        AdvancementHolder advancement = resolve(helper, player, path);
        helper.assertTrue(!player.getAdvancements().getOrStartProgress(advancement).isDone(),
                "advancement " + path + " should NOT be granted");
    }

    private static AdvancementHolder resolve(GameTestHelper helper, ServerPlayer player, String path) {
        AdvancementHolder advancement = helper.getLevel().getServer().getAdvancements().get(Tribulation.id(path));
        helper.assertTrue(advancement != null, "advancement " + path + " should be loaded (datagen output present)");
        return advancement;
    }
}
