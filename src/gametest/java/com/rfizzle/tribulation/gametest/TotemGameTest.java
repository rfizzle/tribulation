// Tier: 3 (Fabric Gametest)
package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.event.HardcoreHeartsHandler;
import com.rfizzle.tribulation.event.TotemPenaltyHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TotemGameTest implements FabricGameTest {

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void defaultBehavior_noDeathReliefOnTotemPop(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);

        state.setLevel(player.getUUID(), 100, 250);
        int levelBefore = state.getLevel(player.getUUID());

        // Default config: totems.countsAsDeathRelief = false
        TotemPenaltyHandler.onTotemUsed(player);

        int levelAfter = state.getLevel(player.getUUID());
        helper.assertValueEqual(levelAfter, levelBefore, "Level should not change on totem pop by default");
        player.discard();
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void countsAsDeathRelief_true_reducesLevel(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        TribulationConfig cfg = Tribulation.getConfig();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);

        boolean savedTotemRelief = cfg.totems.countsAsDeathRelief;
        boolean savedReliefEnabled = cfg.deathRelief.enabled;
        cfg.totems.countsAsDeathRelief = true;
        cfg.deathRelief.enabled = true;

        try {
            state.setLevel(player.getUUID(), 100, 250);
            int levelBefore = state.getLevel(player.getUUID());

            TotemPenaltyHandler.onTotemUsed(player);

            int levelAfter = state.getLevel(player.getUUID());
            helper.assertValueEqual(levelAfter, levelBefore - cfg.deathRelief.amount, "Level should be reduced by deathRelief.amount");
        } finally {
            cfg.totems.countsAsDeathRelief = savedTotemRelief;
            cfg.deathRelief.enabled = savedReliefEnabled;
            player.discard();
        }
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void countsAsDeathRelief_cooldown_honored(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        TribulationConfig cfg = Tribulation.getConfig();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);

        boolean savedTotemRelief = cfg.totems.countsAsDeathRelief;
        boolean savedReliefEnabled = cfg.deathRelief.enabled;
        cfg.totems.countsAsDeathRelief = true;
        cfg.deathRelief.enabled = true;

        try {
            state.setLevel(player.getUUID(), 100, 250);
            int levelBefore = state.getLevel(player.getUUID());

            // First pop
            TotemPenaltyHandler.onTotemUsed(player);
            int afterFirst = state.getLevel(player.getUUID());
            helper.assertValueEqual(afterFirst, levelBefore - cfg.deathRelief.amount, "First pop should reduce level");

            // Second pop immediately (within cooldown)
            TotemPenaltyHandler.onTotemUsed(player);
            int afterSecond = state.getLevel(player.getUUID());
            helper.assertValueEqual(afterSecond, afterFirst, "Second pop within cooldown should NOT reduce level");
        } finally {
            cfg.totems.countsAsDeathRelief = savedTotemRelief;
            cfg.deathRelief.enabled = savedReliefEnabled;
            player.discard();
        }
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void countsAsDeathRelief_parentDisabled_noLevelChange(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        TribulationConfig cfg = Tribulation.getConfig();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);

        boolean savedTotemRelief = cfg.totems.countsAsDeathRelief;
        boolean savedReliefEnabled = cfg.deathRelief.enabled;
        // Self-contradictory config: opted into totem relief, but the system is off.
        cfg.totems.countsAsDeathRelief = true;
        cfg.deathRelief.enabled = false;

        try {
            state.setLevel(player.getUUID(), 100, 250);
            int levelBefore = state.getLevel(player.getUUID());

            TotemPenaltyHandler.onTotemUsed(player);

            int levelAfter = state.getLevel(player.getUUID());
            helper.assertValueEqual(levelAfter, levelBefore, "Level should not change when deathRelief is disabled");
        } finally {
            cfg.totems.countsAsDeathRelief = savedTotemRelief;
            cfg.deathRelief.enabled = savedReliefEnabled;
            player.discard();
        }
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void protectsHearts_parentDisabled_noHeartsLost(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        TribulationConfig cfg = Tribulation.getConfig();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);

        boolean savedHeartsEnabled = cfg.hardcoreHearts.enabled;
        boolean savedProtect = cfg.totems.protectsHearts;
        // Self-contradictory config: opted out of heart protection, but the system is off.
        cfg.hardcoreHearts.enabled = false;
        cfg.totems.protectsHearts = false;

        try {
            state.resetHearts(player.getUUID());
            HardcoreHeartsHandler.applyModifier(player);

            int heartsBefore = state.getHeartsLost(player.getUUID());

            TotemPenaltyHandler.onTotemUsed(player);

            int heartsAfter = state.getHeartsLost(player.getUUID());
            helper.assertValueEqual(heartsAfter, heartsBefore, "Hearts lost counter should not increment when hardcoreHearts is disabled");
        } finally {
            cfg.hardcoreHearts.enabled = savedHeartsEnabled;
            cfg.totems.protectsHearts = savedProtect;
            player.discard();
        }
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void defaultBehavior_protectsHearts_true_noHeartsLost(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        TribulationConfig cfg = Tribulation.getConfig();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);

        boolean savedHeartsEnabled = cfg.hardcoreHearts.enabled;
        cfg.hardcoreHearts.enabled = true; // System enabled, but totem protects by default

        try {
            state.resetHearts(player.getUUID());
            HardcoreHeartsHandler.applyModifier(player);

            TotemPenaltyHandler.onTotemUsed(player);

            helper.succeedWhen(() -> {
                AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
                helper.assertValueEqual((float) attr.getValue(), 20.0f, "Max health should remain 20 by default totem pop");
            });
        } finally {
            cfg.hardcoreHearts.enabled = savedHeartsEnabled;
            player.discard();
        }
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void protectsHearts_false_deductsHearts(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        TribulationConfig cfg = Tribulation.getConfig();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);

        boolean savedHeartsEnabled = cfg.hardcoreHearts.enabled;
        boolean savedProtect = cfg.totems.protectsHearts;
        cfg.hardcoreHearts.enabled = true;
        cfg.totems.protectsHearts = false;

        try {
            state.resetHearts(player.getUUID());
            HardcoreHeartsHandler.applyModifier(player);

            TotemPenaltyHandler.onTotemUsed(player);

            helper.succeedWhen(() -> {
                AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
                float expected = 20.0f - cfg.hardcoreHearts.heartsLostPerDeath;
                helper.assertValueEqual((float) attr.getValue(), expected, "Max health should be reduced when protectsHearts=false");
            });
        } finally {
            cfg.hardcoreHearts.enabled = savedHeartsEnabled;
            cfg.totems.protectsHearts = savedProtect;
            player.discard();
        }
    }

    /**
     * Regression for the totem mixin {@code ClassCastException}: {@code checkTotemDeathProtection}
     * returns a primitive {@code boolean} in 1.21.1, and it runs for every {@link
     * net.minecraft.world.entity.LivingEntity}, not just players. A non-player mob popping a totem
     * must route through the mixin's {@code RETURN} injection without crashing.
     */
    @GameTest(template = "tribulation:empty_3x3")
    public void totemPop_onNonPlayerEntity_doesNotCrash(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, BlockPos.ZERO);
        zombie.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));

        DamageSource lethal = zombie.damageSources().generic();
        // Enough to be lethal; the totem should intercept and the mixin RETURN handler must not throw.
        boolean survived = zombie.hurt(lethal, 1000.0f);

        helper.assertTrue(survived, "Lethal hit should register as damage taken");
        helper.assertTrue(zombie.isAlive(), "Zombie should survive via totem pop");
        helper.assertTrue(
                zombie.getItemInHand(InteractionHand.OFF_HAND).isEmpty(),
                "Totem should have been consumed");
        zombie.discard();
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void soulInventory_neverTriggeredByTotemPop(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TribulationConfig cfg = Tribulation.getConfig();

        boolean savedSoulEnabled = cfg.soulInventory.enabled;
        cfg.soulInventory.enabled = true;

        try {
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 64));
            helper.assertFalse(player.getInventory().getItem(0).isEmpty(), "Inventory should have item");

            TotemPenaltyHandler.onTotemUsed(player);

            helper.succeedWhen(() -> {
                helper.assertFalse(player.getInventory().getItem(0).isEmpty(), "Inventory should still have item after totem pop");
            });
        } finally {
            cfg.soulInventory.enabled = savedSoulEnabled;
            player.discard();
        }
    }
}
