// Tier: 3 (Fabric Gametest)
package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.event.HardcoreHeartsHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class DeathPenaltiesGameTest implements FabricGameTest {

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void hardcoreHearts_deathReducesMaxHealth(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        MinecraftServer server = helper.getLevel().getServer();
        TribulationConfig cfg = Tribulation.getConfig();

        boolean savedEnabled = cfg.hardcoreHearts.enabled;
        cfg.hardcoreHearts.enabled = true;

        try {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            state.addHeartsLost(player.getUUID(), 4, cfg.hardcoreHearts.minimumHearts);
            HardcoreHeartsHandler.applyModifier(player);

            helper.succeedWhen(() -> {
                AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
                helper.assertFalse(attr == null, "player must have max_health attribute");
                AttributeModifier mod = attr.getModifier(HardcoreHeartsHandler.MODIFIER_ID);
                helper.assertFalse(mod == null, "hardcore_hearts modifier must be present");
                helper.assertValueEqual((float) mod.amount(), -4.0f, "modifier amount");
                helper.assertValueEqual((float) attr.getValue(), 16.0f, "effective max health");
            });
        } finally {
            cfg.hardcoreHearts.enabled = savedEnabled;
        }
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void hardcoreHearts_fragmentRestores(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        MinecraftServer server = helper.getLevel().getServer();
        TribulationConfig cfg = Tribulation.getConfig();

        boolean savedEnabled = cfg.hardcoreHearts.enabled;
        cfg.hardcoreHearts.enabled = true;

        try {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            state.addHeartsLost(player.getUUID(), 6, cfg.hardcoreHearts.minimumHearts);
            HardcoreHeartsHandler.applyModifier(player);

            state.restoreHearts(player.getUUID(), 2);
            HardcoreHeartsHandler.applyModifier(player);

            helper.succeedWhen(() -> {
                AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
                helper.assertFalse(attr == null, "player must have max_health attribute");
                AttributeModifier mod = attr.getModifier(HardcoreHeartsHandler.MODIFIER_ID);
                helper.assertFalse(mod == null, "modifier must be present after restore");
                helper.assertValueEqual((float) mod.amount(), -4.0f, "modifier should reflect 4 lost after restoring 2");
                helper.assertValueEqual((float) attr.getValue(), 16.0f, "effective max health after restore");
            });
        } finally {
            cfg.hardcoreHearts.enabled = savedEnabled;
        }
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void soulInventory_nonSoulboundVoided(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        TribulationConfig cfg = Tribulation.getConfig();

        boolean savedEnabled = cfg.soulInventory.enabled;
        boolean savedRespect = cfg.soulInventory.respectKeepInventory;
        cfg.soulInventory.enabled = true;
        cfg.soulInventory.respectKeepInventory = false;

        try {
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
            player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 32));
            player.getInventory().setItem(2, new ItemStack(Items.GOLDEN_APPLE, 5));

            com.rfizzle.tribulation.event.SoulInventoryHandler.processDeathInventory(player);

            helper.succeedWhen(() -> {
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    helper.assertTrue(player.getInventory().getItem(i).isEmpty(),
                            "slot " + i + " should be empty after soul inventory voiding");
                }
            });
        } finally {
            cfg.soulInventory.enabled = savedEnabled;
            cfg.soulInventory.respectKeepInventory = savedRespect;
        }
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void soulInventory_soulboundRetained(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        TribulationConfig cfg = Tribulation.getConfig();

        boolean savedEnabled = cfg.soulInventory.enabled;
        boolean savedRespect = cfg.soulInventory.respectKeepInventory;
        cfg.soulInventory.enabled = true;
        cfg.soulInventory.respectKeepInventory = false;

        try {
            ItemStack soulboundSword = new ItemStack(Items.DIAMOND_SWORD);
            var registry = helper.getLevel().getServer().registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            var soulboundKey = net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.ENCHANTMENT,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tribulation", "soulbound"));
            var holderOpt = registry.getHolder(soulboundKey);

            if (holderOpt.isEmpty()) {
                helper.succeed();
                return;
            }

            soulboundSword.enchant(holderOpt.get(), 1);
            player.getInventory().setItem(0, soulboundSword);
            player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 32));

            com.rfizzle.tribulation.event.SoulInventoryHandler.processDeathInventory(player);

            helper.succeedWhen(() -> {
                helper.assertTrue(player.getInventory().getItem(0).isEmpty(),
                        "soulbound item cleared from old player (stashed for COPY_FROM)");
                helper.assertTrue(player.getInventory().getItem(1).isEmpty(),
                        "non-soulbound item should be voided");
            });
        } finally {
            cfg.soulInventory.enabled = savedEnabled;
            cfg.soulInventory.respectKeepInventory = savedRespect;
        }
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void soulInventory_respectsKeepInventory(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        TribulationConfig cfg = Tribulation.getConfig();

        boolean savedEnabled = cfg.soulInventory.enabled;
        boolean savedRespect = cfg.soulInventory.respectKeepInventory;
        cfg.soulInventory.enabled = true;
        cfg.soulInventory.respectKeepInventory = true;

        boolean originalKeepInv = helper.getLevel().getGameRules()
                .getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY);
        helper.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY)
                .set(true, helper.getLevel().getServer());

        try {
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
            player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 32));

            com.rfizzle.tribulation.event.SoulInventoryHandler.processDeathInventory(player);

            helper.succeedWhen(() -> {
                helper.assertFalse(player.getInventory().getItem(0).isEmpty(),
                        "items should be retained when keepInventory is on");
                helper.assertFalse(player.getInventory().getItem(1).isEmpty(),
                        "all items should be retained when keepInventory is on");
            });
        } finally {
            cfg.soulInventory.enabled = savedEnabled;
            cfg.soulInventory.respectKeepInventory = savedRespect;
            helper.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY)
                    .set(originalKeepInv, helper.getLevel().getServer());
        }
    }
}
