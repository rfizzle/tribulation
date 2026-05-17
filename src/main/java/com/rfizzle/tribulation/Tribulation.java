package com.rfizzle.tribulation;

import com.rfizzle.tribulation.command.TribulationCommand;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.event.DeathReliefHandler;
import com.rfizzle.tribulation.event.HardcoreHeartsHandler;
import com.rfizzle.tribulation.event.MobScalingHandler;
import com.rfizzle.tribulation.event.ShardDropHandler;
import com.rfizzle.tribulation.event.SoulInventoryHandler;
import com.rfizzle.tribulation.event.XpLootHandler;
import com.rfizzle.tribulation.item.TribulationItems;
import com.rfizzle.tribulation.scaling.TierManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tribulation implements ModInitializer {
    public static final String MOD_ID = "tribulation";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final int TICK_INTERVAL = 20;

    private static volatile TribulationConfig config;

    @Override
    public void onInitialize() {
        LOGGER.info("Tribulation initializing");
        config = TribulationConfig.load();
        TribulationItems.register();
        registerTickHandler();
        MobScalingHandler.register();
        DeathReliefHandler.register();
        HardcoreHeartsHandler.register();
        SoulInventoryHandler.register();
        ShardDropHandler.register();
        XpLootHandler.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                TribulationCommand.register(dispatcher));
    }

    public static TribulationConfig getConfig() {
        return config;
    }

    public static void reloadConfig() {
        config = TribulationConfig.load();
    }

    private static void registerTickHandler() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            TribulationConfig cfg = config;
            if (cfg == null || !cfg.timeScaling.enabled) {
                return;
            }
            if (server.getTickCount() % TICK_INTERVAL != 0) {
                return;
            }
            if (server.getPlayerList().getPlayerCount() == 0) {
                return;
            }
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            int levelUpTicks = cfg.general.levelUpTicks;
            int maxLevel = cfg.general.maxLevel;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                int oldLevel = state.getLevel(player.getUUID());
                int oldTier = TierManager.getTier(oldLevel, cfg.tiers);
                int levelsGained = state.incrementTick(player.getUUID(), TICK_INTERVAL, levelUpTicks, maxLevel);
                if (levelsGained > 0 && cfg.general.notifyLevelUp) {
                    int newLevel = state.getLevel(player.getUUID());
                    int newTier = TierManager.getTier(newLevel, cfg.tiers);
                    sendLevelUpMessage(player, newLevel, oldTier, newTier, maxLevel, cfg.general.notifyLevelUpShowTier);
                }
            }
        });
    }

    private static void sendLevelUpMessage(ServerPlayer player, int newLevel, int oldTier, int newTier, int maxLevel, boolean showTier) {
        Component message;
        if (newLevel >= maxLevel) {
            // One-time "hit the cap" message; suppress the normal level_up line
            // so players don't receive two pings on the same tick.
            message = Component.translatable("message.tribulation.level_max")
                    .withStyle(ChatFormatting.GREEN);
        } else if (showTier && newTier != oldTier) {
            message = Component.translatable("message.tribulation.level_up_tier", newLevel, newTier)
                    .withStyle(ChatFormatting.GREEN);
        } else {
            message = Component.translatable("message.tribulation.level_up", newLevel)
                    .withStyle(ChatFormatting.GREEN);
        }
        player.sendSystemMessage(message);
    }
}
