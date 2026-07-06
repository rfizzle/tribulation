package com.rfizzle.tribulation;

import com.rfizzle.tribulation.advancement.TribulationCriteria;
import com.rfizzle.tribulation.api.TribulationLevelCallback;
import com.rfizzle.tribulation.command.TribulationCommand;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.event.BloodMoonHandler;
import com.rfizzle.tribulation.event.ChampionEffectHandler;
import com.rfizzle.tribulation.event.DeathReliefHandler;
import com.rfizzle.tribulation.event.EnvironmentalPressureHandler;
import com.rfizzle.tribulation.event.HardcoreHeartsHandler;
import com.rfizzle.tribulation.event.LevelDecayHandler;
import com.rfizzle.tribulation.event.MobScalingHandler;
import com.rfizzle.tribulation.event.PackTacticsHandler;
import com.rfizzle.tribulation.event.RaidScalingHandler;
import com.rfizzle.tribulation.event.ShardDropHandler;
import com.rfizzle.tribulation.event.SilverfishAbilityHandler;
import com.rfizzle.tribulation.event.SoulInventoryHandler;
import com.rfizzle.tribulation.item.TribulationItems;
import com.rfizzle.tribulation.sound.TribulationSounds;
import com.rfizzle.tribulation.stat.TribulationStats;
import com.rfizzle.tribulation.network.TribulationNetworking;
import com.rfizzle.tribulation.scaling.StructureBoostManager;
import com.rfizzle.tribulation.scaling.TierManager;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
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
        com.rfizzle.tribulation.data.TribulationAttachments.register();
        TribulationNetworking.register();
        TribulationItems.register();
        TribulationSounds.register();
        com.rfizzle.tribulation.particle.TribulationParticles.register();
        TribulationStats.register();
        TribulationCriteria.register();
        registerTickHandler();
        StructureBoostManager.register();
        MobScalingHandler.register();
        ChampionEffectHandler.register();
        BloodMoonHandler.register();
        RaidScalingHandler.register();
        DeathReliefHandler.register();
        LevelDecayHandler.register();
        HardcoreHeartsHandler.register();
        SoulInventoryHandler.register();
        ShardDropHandler.register();
        SilverfishAbilityHandler.register();
        PackTacticsHandler.register();
        EnvironmentalPressureHandler.register();
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
            TribulationNetworking.syncLevel(player);
            TribulationNetworking.syncBloodMoon(player, BloodMoonHandler.syncedStateFor(config));
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
                // Push the advancing tick counter every interval so the HUD badge
                // and tier panel animate between level-ups, not just on join and
                // level boundaries. This carries any level gain from the call above.
                TribulationNetworking.syncLevel(player);
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
            int newTier = TierManager.getTier(newLevel, cfg.tiers);
            TribulationLevelCallback.EVENT.invoker().onLevelChanged(player, oldLevel, newLevel);
            if (newTier != oldTier) {
                onTierCrossed(player, newTier);
            }
            if (cfg.general.notifyLevelUp) {
                // First notified level-up carries a one-time sentence explaining
                // the system. Gated on a persisted flag rather than oldLevel == 0
                // so a level that decays back to 0 and re-climbs never repeats it.
                // The flag is marked only when the sentence is actually shown, so a
                // degenerate cap-on-first-level (which suppresses it) doesn't burn
                // the one-shot without the player ever seeing it.
                boolean wantIntro = !state.hasSeenLevelUpIntro(player.getUUID());
                boolean introShown = sendLevelUpMessage(player, newLevel, oldTier, newTier,
                        cfg.general.maxLevel, cfg.general.notifyLevelUpShowTier, wantIntro);
                if (introShown) {
                    state.markLevelUpIntroSeen(player.getUUID());
                }
            }
        }
    }

    /**
     * Mark a tier crossing as a moment: grant the matching tier advancement
     * (toast handled by the advancement) and play the {@code tier_up} sting to
     * the leveling player only. The sound is sent straight to the player's
     * connection — {@code Level#playSound(null, …)} would broadcast to everyone
     * nearby, and {@code Player#playNotifySound} is a client-only no-op on a
     * dedicated server.
     */
    private static void onTierCrossed(ServerPlayer player, int newTier) {
        TribulationCriteria.TIER_REACHED.trigger(player, newTier);
        player.connection.send(new ClientboundSoundPacket(
                TribulationSounds.TIER_UP,
                SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(),
                1.0f, 1.0f,
                player.getRandom().nextLong()));
    }

    /**
     * Send the level-up chat line. Returns {@code true} when the one-time
     * teaching sentence was actually appended, so the caller only burns the
     * one-shot flag on a message the player really saw.
     */
    private static boolean sendLevelUpMessage(ServerPlayer player, int newLevel, int oldTier, int newTier, int maxLevel, boolean showTier, boolean showIntro) {
        MutableComponent message;
        boolean cappedNow = newLevel >= maxLevel;
        if (cappedNow) {
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
        // On the first level-up, append one sentence teaching how leveling
        // works. Skipped on the degenerate cap-on-first-level case, where the
        // "maximum difficulty" line already reads as terminal.
        boolean introShown = showIntro && !cappedNow;
        if (introShown) {
            message = message.append(CommonComponents.SPACE)
                    .append(Component.translatable("message.tribulation.level_up_intro"));
        }
        player.sendSystemMessage(message);
        return introShown;
    }
}
