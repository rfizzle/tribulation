package com.rfizzle.tribulation;

import com.rfizzle.tribulation.api.TribulationLevelCallback;
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
import com.rfizzle.tribulation.network.TribulationNetworking;
import com.rfizzle.tribulation.scaling.TierManager;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
        TribulationNetworking.register();
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
        registerJoinSync();
    }

    public static TribulationConfig getConfig() {
        return config;
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static void reloadConfig() {
        config = TribulationConfig.load();
    }

    private static void registerJoinSync() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            int level = state.getLevel(player.getUUID());
            TribulationNetworking.syncLevel(player);
        });
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
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                applyLevelTick(player, state, cfg, TICK_INTERVAL);
            }
        });
    }

    /**
     * Advance one player's tick counter by {@code ticksToAdd}, and fire
     * {@link TribulationLevelCallback} if a level boundary is crossed. The
     * production tick handler calls this with {@link #TICK_INTERVAL}; tests
     * may pass {@code cfg.general.levelUpTicks} to force a level-up.
     */
    public static void applyLevelTick(ServerPlayer player, PlayerDifficultyState state, TribulationConfig cfg, int ticksToAdd) {
        int oldLevel = state.getLevel(player.getUUID());
        int oldTier = TierManager.getTier(oldLevel, cfg.tiers);
        int levelsGained = state.incrementTick(player.getUUID(), ticksToAdd, cfg.general.levelUpTicks, cfg.general.maxLevel);
        if (levelsGained > 0) {
            int newLevel = state.getLevel(player.getUUID());
            TribulationNetworking.syncLevel(player);
            TribulationLevelCallback.EVENT.invoker().onLevelChanged(player, oldLevel, newLevel);
            if (cfg.general.notifyLevelUp) {
                int newTier = TierManager.getTier(newLevel, cfg.tiers);
                sendLevelUpMessage(player, newLevel, oldTier, newTier, cfg.general.maxLevel, cfg.general.notifyLevelUpShowTier);
            }
        }
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
