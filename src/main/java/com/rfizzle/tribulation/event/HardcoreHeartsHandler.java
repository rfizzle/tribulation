package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.stat.TribulationStats;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class HardcoreHeartsHandler {

    public static final ResourceLocation MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            Tribulation.MOD_ID, "hardcore_hearts");

    private HardcoreHeartsHandler() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(HardcoreHeartsHandler::onAfterDeath);
        ServerPlayerEvents.COPY_FROM.register(HardcoreHeartsHandler::onCopyFrom);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                applyModifier(handler.getPlayer()));
    }

    static void onAfterDeath(LivingEntity entity, DamageSource damageSource) {
        if (!(entity instanceof ServerPlayer player)) return;

        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.hardcoreHearts.enabled) return;

        applyPenalty(player);
    }

    public static void applyPenalty(ServerPlayer player) {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;

        try {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            int before = state.getHeartsLost(player.getUUID());
            int after = state.addHeartsLost(
                    player.getUUID(),
                    cfg.hardcoreHearts.heartsLostPerDeath,
                    cfg.hardcoreHearts.minimumHearts
            );
            if (after > before) {
                player.awardStat(TribulationStats.HEARTS_LOST, after - before);
                int currentMax = 20 - after;
                int maxPenalty = 20 - cfg.hardcoreHearts.minimumHearts;
                if (after >= maxPenalty) {
                    player.sendSystemMessage(
                            Component.translatable("message.tribulation.heart_lost_floor")
                                    .withStyle(ChatFormatting.DARK_RED));
                } else {
                    player.sendSystemMessage(
                            Component.translatable("message.tribulation.heart_lost",
                                    currentMax / 2, 10)
                                    .withStyle(ChatFormatting.RED));
                }
                applyModifier(player);
            }
        } catch (Exception e) {
            Tribulation.LOGGER.error("Failed to apply hardcore hearts penalty for {}", player, e);
        }
    }

    static void onCopyFrom(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        if (alive) return;
        applyModifier(newPlayer);
    }

    public static void applyModifier(ServerPlayer player) {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.hardcoreHearts.enabled) {
            removeModifier(player);
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) return;

        try {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            int heartsLost = state.getHeartsLost(player.getUUID());
            AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
            if (attr == null) return;

            attr.removeModifier(MODIFIER_ID);
            if (heartsLost > 0) {
                attr.addPermanentModifier(new AttributeModifier(
                        MODIFIER_ID,
                        -heartsLost,
                        AttributeModifier.Operation.ADD_VALUE
                ));
            }
        } catch (Exception e) {
            Tribulation.LOGGER.error("Failed to apply hardcore hearts modifier for {}", player, e);
        }
    }

    private static void removeModifier(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.removeModifier(MODIFIER_ID);
        }
    }
}
