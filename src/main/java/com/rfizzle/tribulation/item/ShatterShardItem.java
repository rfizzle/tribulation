package com.rfizzle.tribulation.item;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
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
 * Consumable shard that lowers the using player's difficulty level by
 * {@link TribulationConfig.Shards#shardPower}, floored at
 * {@link TribulationConfig.DeathRelief#minimumLevel}. When
 * {@code shards.sideEffects} is enabled, the user also takes a short Slowness
 * II / Mining Fatigue II / Weakness II debuff. The stack is consumed in
 * survival/adventure (creative keeps the item).
 */
public class ShatterShardItem extends Item {
    public static final int SIDE_EFFECT_DURATION_TICKS = 200;
    public static final int SIDE_EFFECT_AMPLIFIER = 1;

    public ShatterShardItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.tribulation.shatter_shard.tooltip"));
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
        if (cfg == null || !cfg.shards.enabled) {
            return InteractionResultHolder.pass(stack);
        }

        MinecraftServer server = serverPlayer.getServer();
        if (server == null) {
            return InteractionResultHolder.pass(stack);
        }

        try {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            int before = state.getLevel(serverPlayer.getUUID());
            int after = state.reducePlayerLevel(
                    serverPlayer.getUUID(),
                    cfg.shards.shardPower,
                    cfg.deathRelief.minimumLevel
            );

            if (cfg.shards.sideEffects) {
                applySideEffects(serverPlayer);
            }

            stack.consume(1, serverPlayer);

            level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.8f, 1.2f);

            if (before != after) {
                serverPlayer.displayClientMessage(
                        Component.translatable("item.tribulation.shatter_shard.used", before, after),
                        true
                );
                Tribulation.LOGGER.debug(
                        "Shatter shard used: {} reduced from level {} to {}",
                        serverPlayer.getGameProfile().getName(), before, after
                );
            } else {
                serverPlayer.displayClientMessage(
                        Component.translatable("item.tribulation.shatter_shard.at_floor", before),
                        true
                );
            }
        } catch (Exception e) {
            Tribulation.LOGGER.error("Failed to apply shatter shard for {}", player, e);
            return InteractionResultHolder.fail(stack);
        }

        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    private static void applySideEffects(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                SIDE_EFFECT_DURATION_TICKS, SIDE_EFFECT_AMPLIFIER, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN,
                SIDE_EFFECT_DURATION_TICKS, SIDE_EFFECT_AMPLIFIER, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                SIDE_EFFECT_DURATION_TICKS, SIDE_EFFECT_AMPLIFIER, false, true, true));
    }
}
