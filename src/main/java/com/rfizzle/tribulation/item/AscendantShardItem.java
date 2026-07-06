package com.rfizzle.tribulation.item;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.api.TribulationLevelCallback;
import com.rfizzle.tribulation.compat.common.AscendantInfoFormatter;
import com.rfizzle.tribulation.compat.common.TooltipDetailProvider;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.stat.TribulationStats;
import com.rfizzle.tribulation.network.TribulationNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Consumable shard that raises the using player's difficulty level by
 * {@link TribulationConfig.Ascension#raisePower}, capped at
 * {@link TribulationConfig.General#maxLevel} — the dark twin of the Shatter
 * Shard. The raised level (tougher mobs, champion spawns, richer XP/loot) is
 * the reward; consumption is costless by default. When
 * {@code ascension.sideEffects} is enabled the user also gains a short
 * Strength II / Resistance II buff window. The stack is consumed in
 * survival/adventure (creative keeps the item).
 */
public class AscendantShardItem extends Item {
    public static final int SIDE_EFFECT_DURATION_TICKS = 200;
    public static final int SIDE_EFFECT_AMPLIFIER = 1;

    public AscendantShardItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.tribulation.ascendant_shard.tooltip"));
        if (TooltipDetailProvider.isShiftHeld()) {
            tooltipComponents.add(Component.empty());
            for (Component line : AscendantInfoFormatter.infoLines(TooltipDetailProvider.effectiveConfig())) {
                tooltipComponents.add(line.copy().withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltipComponents.add(Component.translatable("item.tribulation.ascendant_shard.info.hold_shift")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            // Let the client animate the swing; all state mutation happens server-side.
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.ascension.enabled) {
            return InteractionResultHolder.pass(stack);
        }

        MinecraftServer server = serverPlayer.getServer();
        if (server == null) {
            return InteractionResultHolder.pass(stack);
        }

        try {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            int before = state.getLevel(serverPlayer.getUUID());
            int after = state.raisePlayerLevel(
                    serverPlayer.getUUID(),
                    cfg.ascension.raisePower,
                    cfg.general.maxLevel
            );

            if (after <= before) {
                // No upward movement — already at (or above) the ceiling, or a
                // zero raise. Never consume the item or push the level down: an
                // ascendant shard must only ever raise. Keep the item and warn.
                serverPlayer.displayClientMessage(
                        Component.translatable("item.tribulation.ascendant_shard.at_ceiling", before),
                        true
                );
                return InteractionResultHolder.pass(stack);
            }

            if (cfg.ascension.sideEffects) {
                applySideEffects(serverPlayer);
            }

            serverPlayer.awardStat(TribulationStats.ASCENDANT_SHARDS_USED);
            stack.consume(1, serverPlayer);
            com.rfizzle.tribulation.advancement.TribulationCriteria.ASCENDANT_SHARD_USED.trigger(serverPlayer);

            level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.8f, 0.8f);

            TribulationNetworking.syncLevel(serverPlayer);

            TribulationLevelCallback.EVENT.invoker().onLevelChanged(serverPlayer, before, after);
            serverPlayer.displayClientMessage(
                    Component.translatable("item.tribulation.ascendant_shard.used", before, after),
                    true
            );
            Tribulation.LOGGER.debug(
                    "Ascendant shard used: {} raised from level {} to {}",
                    serverPlayer.getGameProfile().getName(), before, after
            );
        } catch (Exception e) {
            Tribulation.LOGGER.error("Failed to apply ascendant shard for {}", player, e);
            return InteractionResultHolder.fail(stack);
        }

        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    private static void applySideEffects(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,
                SIDE_EFFECT_DURATION_TICKS, SIDE_EFFECT_AMPLIFIER, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                SIDE_EFFECT_DURATION_TICKS, SIDE_EFFECT_AMPLIFIER, false, true, true));
    }
}
