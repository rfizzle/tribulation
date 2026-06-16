package com.rfizzle.tribulation.item;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.event.HardcoreHeartsHandler;
import com.rfizzle.tribulation.stat.TribulationStats;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Consumable item that restores hearts lost to the Hardcore Hearts system.
 * Restores {@link TribulationConfig.HardcoreHearts#heartsRestoredPerFragment}
 * half-hearts per use. No-ops when the player has no heart penalty.
 */
public class HeartFragmentItem extends Item {

    public HeartFragmentItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.tribulation.heart_fragment.tooltip"));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.hardcoreHearts.enabled) {
            return InteractionResultHolder.pass(stack);
        }

        MinecraftServer server = serverPlayer.getServer();
        if (server == null) {
            return InteractionResultHolder.pass(stack);
        }

        try {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            int before = state.getHeartsLost(serverPlayer.getUUID());

            if (before == 0) {
                serverPlayer.displayClientMessage(
                        Component.translatable("item.tribulation.heart_fragment.full"),
                        true
                );
                return InteractionResultHolder.pass(stack);
            }

            int after = state.restoreHearts(
                    serverPlayer.getUUID(),
                    cfg.hardcoreHearts.heartsRestoredPerFragment
            );

            serverPlayer.awardStat(TribulationStats.HEARTS_RESTORED, before - after);
            HardcoreHeartsHandler.applyModifier(serverPlayer);
            stack.consume(1, serverPlayer);

            level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.4f);

            int currentMax = 20 - after;
            serverPlayer.displayClientMessage(
                    Component.translatable("item.tribulation.heart_fragment.used",
                            (before - after) / 2, currentMax / 2, 10),
                    true
            );
        } catch (Exception e) {
            Tribulation.LOGGER.error("Failed to apply heart fragment for {}", player, e);
            return InteractionResultHolder.fail(stack);
        }

        return InteractionResultHolder.sidedSuccess(stack, false);
    }
}
