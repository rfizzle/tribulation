package com.rfizzle.tribulation.command;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.api.TribulationLevelCallback;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.config.TribulationConfig.MobScaling;
import com.rfizzle.tribulation.compat.common.MobScalingDataCollector;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.event.HardcoreHeartsHandler;
import com.rfizzle.tribulation.event.MobScalingHandler;
import com.rfizzle.tribulation.event.SoulInventoryHandler;
import com.rfizzle.tribulation.event.SkeletonVariantHandler;
import com.rfizzle.tribulation.event.ZombieVariantHandler;
import com.rfizzle.tribulation.network.TribulationNetworking;
import com.rfizzle.tribulation.scaling.BossScalingEngine;
import com.rfizzle.tribulation.scaling.ScalingEngine;
import com.rfizzle.tribulation.scaling.TierManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /tribulation} subcommands from DESIGN.md. Permission levels:
 * {@code info} is self-service and readable by anyone (0); {@code level
 * <player>} is an admin lookup for other players (2); everything else also
 * requires op (2). All mutations route through {@link PlayerDifficultyState}
 * so they persist across restarts and trigger {@code setDirty()} correctly.
 */
public final class TribulationCommand {
    public static final String ROOT = "tribulation";
    public static final double INSPECT_RANGE = 10.0;

    private TribulationCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal(ROOT)
                        .then(Commands.literal("level")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(TribulationCommand::runLevel)))
                        .then(Commands.literal("set")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("level", IntegerArgumentType.integer(0))
                                                .executes(TribulationCommand::runSet))))
                        .then(Commands.literal("reset")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(TribulationCommand::runReset)))
                        .then(Commands.literal("reload")
                                .requires(src -> src.hasPermission(2))
                                .executes(TribulationCommand::runReload))
                        .then(Commands.literal("info")
                                .executes(TribulationCommand::runInfo))
                        .then(Commands.literal("config")
                                .requires(src -> src.hasPermission(2))
                                .executes(TribulationCommand::runConfig))
                        .then(Commands.literal("debug")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(TribulationCommand::runDebug)))
                        .then(Commands.literal("inspect")
                                .requires(src -> src.hasPermission(2))
                                .executes(TribulationCommand::runInspect))
                        .then(Commands.literal("hearts")
                                .executes(TribulationCommand::runHeartsSelf)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(src -> src.hasPermission(2))
                                        .executes(TribulationCommand::runHearts)
                                        .then(Commands.literal("restore")
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(TribulationCommand::runHeartsRestore)))
                                        .then(Commands.literal("reset")
                                                .executes(TribulationCommand::runHeartsReset))))
                        .then(Commands.literal("inventory")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(TribulationCommand::runInventory)))
        );
    }

    // ---- Subcommand implementations ----

    private static int runLevel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        CommandSourceStack src = ctx.getSource();
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null) {
            src.sendFailure(Component.literal("Tribulation config not loaded"));
            return 0;
        }
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(src.getServer());
        UUID uuid = target.getUUID();
        int level = state.getLevel(uuid);
        int tickCounter = state.getTickCounter(uuid);
        int levelUpTicks = Math.max(1, cfg.general.levelUpTicks);
        int remaining = Math.max(0, levelUpTicks - tickCounter);

        src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "%s is level %d / %d (%d ticks to next, %s remaining)",
                target.getGameProfile().getName(), level, cfg.general.maxLevel,
                remaining, formatTicksAsDuration(remaining))), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int runSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int requested = IntegerArgumentType.getInteger(ctx, "level");
        CommandSourceStack src = ctx.getSource();
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null) {
            src.sendFailure(Component.literal("Tribulation config not loaded"));
            return 0;
        }
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(src.getServer());
        int actual = applySetLevel(target, requested, state, cfg);
        src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Set %s to level %d", target.getGameProfile().getName(), actual)), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Core of the {@code /tribulation set} operation. Sets the player's level,
     * syncs it to the client, and fires {@link TribulationLevelCallback} if the
     * level changed. Exposed for integration testing.
     */
    public static int applySetLevel(ServerPlayer target, int requested, PlayerDifficultyState state, TribulationConfig cfg) {
        int oldLevel = state.getLevel(target.getUUID());
        int actual = state.setLevel(target.getUUID(), requested, cfg.general.maxLevel);
        TribulationNetworking.syncLevel(target);
        if (oldLevel != actual) {
            TribulationLevelCallback.EVENT.invoker().onLevelChanged(target, oldLevel, actual);
        }
        return actual;
    }

    private static int runReset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        CommandSourceStack src = ctx.getSource();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(src.getServer());
        int oldLevel = state.getLevel(target.getUUID());
        state.reset(target.getUUID());
        int newLevel = state.getLevel(target.getUUID());
        TribulationNetworking.syncLevel(target);
        if (oldLevel != newLevel) {
            TribulationLevelCallback.EVENT.invoker().onLevelChanged(target, oldLevel, newLevel);
        }
        src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Reset %s to level 0", target.getGameProfile().getName())), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int runReload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            Tribulation.reloadConfig();
            src.sendSuccess(() -> Component.literal("Reloaded Tribulation config"), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            Tribulation.LOGGER.error("Config reload failed via command", e);
            src.sendFailure(Component.literal("Config reload failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int runInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null) {
            src.sendFailure(Component.literal("Tribulation config not loaded"));
            return 0;
        }
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(src.getServer());
        UUID uuid = player.getUUID();
        for (String line : formatPlayerInfo(
                player.getGameProfile().getName(),
                state.getLevel(uuid),
                state.getTickCounter(uuid),
                state.getHeartsLost(uuid),
                cfg)) {
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int runConfig(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null) {
            src.sendFailure(Component.literal("Tribulation config not loaded"));
            return 0;
        }
        for (String line : formatConfigSummary(cfg)) {
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int runDebug(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        CommandSourceStack src = ctx.getSource();
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null) {
            src.sendFailure(Component.literal("Tribulation config not loaded"));
            return 0;
        }
        if (!(target.level() instanceof ServerLevel world)) {
            src.sendFailure(Component.literal("Target is not in a server level"));
            return 0;
        }

        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(src.getServer());
        int level = state.getLevel(target.getUUID());
        int effectiveLevel = ScalingEngine.getEffectiveLevel(target, world);

        // Reference-mob scaling ("zombie") gives a stable apples-to-apples time
        // breakdown across calls. Per-mob debug is covered by /inspect.
        MobScaling refScaling = cfg.getMobScaling("zombie");

        double horizDist = horizontalDistanceFromSpawn(world, target.getX(), target.getZ());
        double rawDistFactor = ScalingEngine.distanceAppliesInDimension(world, cfg.distanceScaling)
                ? ScalingEngine.computeDistanceFactor(horizDist, cfg.distanceScaling)
                : 0.0;
        double rawHeightFactor = ScalingEngine.heightAppliesInDimension(world, cfg.heightScaling)
                ? ScalingEngine.computeHeightFactor(target.getY(), cfg.heightScaling)
                : 0.0;
        double rawMoonFactor = ScalingEngine.moonAppliesAt(world, target, cfg.moonPhaseScaling)
                ? ScalingEngine.computeMoonFactor(world.getMoonPhase(), cfg.moonPhaseScaling.maxBonus)
                : 0.0;

        for (String line : formatDebug(target, world, cfg, level, effectiveLevel, refScaling, horizDist, rawDistFactor, rawHeightFactor, rawMoonFactor)) {
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int runInspect(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        Mob mob = raycastMob(player, INSPECT_RANGE);
        if (mob == null) {
            src.sendFailure(Component.literal("No mob found within " + (int) INSPECT_RANGE + " blocks"));
            return 0;
        }
        for (String line : formatInspect(mob)) {
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int runHeartsSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.hardcoreHearts.enabled) {
            src.sendFailure(Component.literal("Hardcore Hearts is disabled"));
            return 0;
        }
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(src.getServer());
        int heartsLost = state.getHeartsLost(player.getUUID());
        int currentMax = 20 - heartsLost;
        if (heartsLost == 0) {
            src.sendSuccess(() -> Component.literal("You have not lost any hearts."), false);
        } else {
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "You have lost %d half-hearts (%d/%d max HP)",
                    heartsLost, currentMax, 20)), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int runHearts(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        CommandSourceStack src = ctx.getSource();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(src.getServer());
        int heartsLost = state.getHeartsLost(target.getUUID());
        int currentMax = 20 - heartsLost;
        src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "%s has lost %d half-hearts (%d/%d max HP)",
                target.getGameProfile().getName(), heartsLost, currentMax, 20)), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int runHeartsRestore(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        CommandSourceStack src = ctx.getSource();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(src.getServer());
        int before = state.getHeartsLost(target.getUUID());
        int after = state.restoreHearts(target.getUUID(), amount);
        HardcoreHeartsHandler.applyModifier(target);
        src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Restored %d half-hearts for %s (%d → %d lost)",
                before - after, target.getGameProfile().getName(), before, after)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int runHeartsReset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        CommandSourceStack src = ctx.getSource();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(src.getServer());
        int prev = state.resetHearts(target.getUUID());
        HardcoreHeartsHandler.applyModifier(target);
        src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Cleared heart penalty for %s (was %d half-hearts lost)",
                target.getGameProfile().getName(), prev)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int runInventory(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        CommandSourceStack src = ctx.getSource();
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.soulInventory.enabled) {
            src.sendFailure(Component.literal("Soul Inventory is disabled"));
            return 0;
        }
        int count = SoulInventoryHandler.countSoulboundItems(target);
        src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "%s has %d soulbound item(s)",
                target.getGameProfile().getName(), count)), false);
        return Command.SINGLE_SUCCESS;
    }

    // ---- Formatting helpers (package-private for tests) ----

    /**
     * Admin-facing config summary — lists every scaling axis, cap, and
     * subsystem toggle. Kept as a pure helper so it can be unit-tested
     * without a server. Permission-gated at the command level (runConfig
     * requires op 2); the formatter itself has no side effects.
     */
    static List<String> formatConfigSummary(TribulationConfig cfg) {
        List<String> lines = new ArrayList<>();
        int levelUpTicks = Math.max(1, cfg.general.levelUpTicks);
        lines.add("=== Tribulation ===");
        lines.add(String.format(Locale.ROOT,
                "Max level: %d (1 level per %s)",
                cfg.general.maxLevel, formatTicksAsDuration(levelUpTicks)));
        lines.add(String.format(Locale.ROOT,
                "Detection range: %.1f blocks",
                cfg.general.mobDetectionRange));
        lines.add(String.format(Locale.ROOT,
                "Axes: time=%s, distance=%s, height=%s",
                onOff(cfg.timeScaling.enabled),
                onOff(cfg.distanceScaling.enabled),
                onOff(cfg.heightScaling.enabled)));
        if (cfg.distanceScaling.enabled) {
            lines.add(String.format(Locale.ROOT,
                    "  Distance: start=%.0f, step=%.0f, rate=%.3f, max=%.3f%s",
                    cfg.distanceScaling.startingDistance,
                    cfg.distanceScaling.increasingDistance,
                    cfg.distanceScaling.distanceFactor,
                    cfg.distanceScaling.maxDistanceFactor,
                    cfg.distanceScaling.excludeInOtherDimensions ? " (overworld only)" : ""));
        }
        if (cfg.heightScaling.enabled) {
            lines.add(String.format(Locale.ROOT,
                    "  Height: base=%.0f, step=%.0f, rate=%.3f, max=%.3f%s",
                    cfg.heightScaling.startingHeight,
                    cfg.heightScaling.heightDistance,
                    cfg.heightScaling.heightFactor,
                    cfg.heightScaling.maxHeightFactor,
                    cfg.heightScaling.excludeInOtherDimensions ? " (overworld only)" : ""));
        }
        lines.add(String.format(Locale.ROOT,
                "Stat caps: health=%.2f, damage=%.2f, speed=%.2f, protection=%.2f, followRange=%.2f",
                cfg.statCaps.maxFactorHealth, cfg.statCaps.maxFactorDamage,
                cfg.statCaps.maxFactorSpeed, cfg.statCaps.maxFactorProtection,
                cfg.statCaps.maxFactorFollowRange));
        lines.add(String.format(Locale.ROOT,
                "Death relief: %s (-%d levels, cooldown %s, floor %d)",
                onOff(cfg.deathRelief.enabled),
                cfg.deathRelief.amount,
                formatTicksAsDuration(cfg.deathRelief.cooldownTicks),
                cfg.deathRelief.minimumLevel));
        lines.add(String.format(Locale.ROOT,
                "Shards: %s (start level %d, -%d per use, drop %.3f%%)",
                onOff(cfg.shards.enabled),
                cfg.shards.dropStartLevel,
                cfg.shards.shardPower,
                cfg.shards.dropChance * 100.0));
        lines.add(String.format(Locale.ROOT,
                "Bosses: %s (max=%.2f, time rate=%.3f, distance rate=%.3f)",
                onOff(cfg.bosses.affectBosses),
                cfg.bosses.bossMaxFactor,
                cfg.bosses.bossTimeFactor,
                cfg.bosses.bossDistanceFactor));
        lines.add(String.format(Locale.ROOT,
                "XP bonus: %s (max %.2fx). Extra loot: %s",
                onOff(cfg.xpAndLoot.extraXp),
                cfg.xpAndLoot.maxXpFactor,
                onOff(cfg.xpAndLoot.dropMoreLoot)));
        lines.add(String.format(Locale.ROOT,
                "Tiers: 1≥%d, 2≥%d, 3≥%d, 4≥%d, 5≥%d",
                cfg.tiers.tier1, cfg.tiers.tier2, cfg.tiers.tier3,
                cfg.tiers.tier4, cfg.tiers.tier5));
        lines.add(String.format(Locale.ROOT,
                "Hardcore Hearts: %s (-%d/death, min %d, +%d/fragment)",
                onOff(cfg.hardcoreHearts.enabled),
                cfg.hardcoreHearts.heartsLostPerDeath,
                cfg.hardcoreHearts.minimumHearts,
                cfg.hardcoreHearts.heartsRestoredPerFragment));
        lines.add(String.format(Locale.ROOT,
                "Soul Inventory: %s (enchant=%s, destroyXP=%s, keepInventory=%s)",
                onOff(cfg.soulInventory.enabled),
                cfg.soulInventory.soulboundEnchantment,
                onOff(cfg.soulInventory.destroyXp),
                onOff(cfg.soulInventory.respectKeepInventory)));
        return lines;
    }

    /**
     * Render the caller's own difficulty info: level, tier, and progress to
     * the next level. Inputs are primitives so this stays pure and testable
     * without a {@link ServerPlayer} or a live {@link PlayerDifficultyState}.
     */
    static List<String> formatPlayerInfo(String name, int level, int tickCounter, int heartsLost, TribulationConfig cfg) {
        List<String> lines = new ArrayList<>();
        int maxLevel = Math.max(1, cfg.general.maxLevel);
        int levelUpTicks = Math.max(1, cfg.general.levelUpTicks);
        int tier = TierManager.getTier(level, cfg.tiers);
        lines.add(String.format(Locale.ROOT, "=== Tribulation: %s ===", name));
        lines.add(String.format(Locale.ROOT,
                "Level: %d / %d (tier %d)",
                level, maxLevel, tier));
        if (level >= maxLevel) {
            lines.add("Progress: max level reached");
        } else {
            int remaining = Math.max(0, levelUpTicks - tickCounter);
            lines.add(String.format(Locale.ROOT,
                    "Progress: %d / %d ticks (%s until next level)",
                    Math.max(0, tickCounter), levelUpTicks, formatTicksAsDuration(remaining)));
        }
        if (cfg.hardcoreHearts.enabled && heartsLost > 0) {
            int currentMax = 20 - heartsLost;
            lines.add(String.format(Locale.ROOT,
                    "Hearts: %d/%d max HP (%d half-hearts lost)",
                    currentMax, 20, heartsLost));
        }
        return lines;
    }

    static List<String> formatDebug(
            ServerPlayer target,
            ServerLevel world,
            TribulationConfig cfg,
            int playerLevel,
            int effectiveLevel,
            MobScaling refScaling,
            double horizontalDistance,
            double rawDistanceFactor,
            double rawHeightFactor,
            double rawMoonFactor
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("=== Debug: " + target.getGameProfile().getName() + " ===");
        lines.add(String.format(Locale.ROOT,
                "Position: (%.1f, %.1f, %.1f) in %s",
                target.getX(), target.getY(), target.getZ(),
                world.dimension().location()));
        lines.add(String.format(Locale.ROOT,
                "Player level: %d  Tier: %d",
                playerLevel, TierManager.getTier(playerLevel, cfg.tiers)));
        lines.add(String.format(Locale.ROOT,
                "Scaling mode: %s  Effective level: %d",
                cfg.general.scalingMode, effectiveLevel));
        lines.add(String.format(Locale.ROOT,
                "Dimension offset: %+d  (%s)",
                cfg.getDimensionOffset(world.dimension().location()),
                world.dimension().location()));
        lines.add(String.format(Locale.ROOT,
                "Distance from spawn: %.1f blocks  →  factor %+.3f",
                horizontalDistance, rawDistanceFactor));

        double heightDelta = target.getY() - cfg.heightScaling.startingHeight;
        lines.add(String.format(Locale.ROOT,
                "Height offset: %+.1f blocks  →  factor %+.3f",
                heightDelta, rawHeightFactor));

        if (rawMoonFactor > 0) {
            lines.add(String.format(Locale.ROOT,
                    "Moon phase: %d  →  factor %+.3f",
                    world.getMoonPhase(), rawMoonFactor));
        } else if (cfg.moonPhaseScaling.enabled) {
            String reason;
            if (cfg.moonPhaseScaling.maxBonus <= 0) {
                reason = "maxBonus is 0";
            } else if (world.isDay()) {
                reason = "day";
            } else if (!world.dimensionType().hasSkyLight() || world.dimensionType().hasCeiling()) {
                reason = "no daylight cycle";
            } else if (cfg.moonPhaseScaling.surfaceOnly && target.getY() < cfg.moonPhaseScaling.surfaceY) {
                reason = String.format(Locale.ROOT, "below surface (Y < %.0f)", cfg.moonPhaseScaling.surfaceY);
            } else {
                reason = "new moon";
            }
            lines.add("Moon: (inactive - " + reason + ")");
        }

        lines.add("Time factors (reference mob: zombie):");
        for (String attr : ScalingEngine.ALL_ATTRIBUTES) {
            double rate = ScalingEngine.rateFor(attr, refScaling);
            double cap = ScalingEngine.capFor(attr, refScaling);
            double timeFactor = ScalingEngine.computeTimeFactor(playerLevel, rate, cap);
            boolean positionScaled = ScalingEngine.isPositionScaled(attr);
            String positionNote = positionScaled
                    ? String.format(Locale.ROOT, ", +dist %+.3f, +height %+.3f, +moon %+.3f", rawDistanceFactor, rawHeightFactor, rawMoonFactor)
                    : " (time only)";
            lines.add(String.format(Locale.ROOT,
                    "  %-12s rate=%.4f cap=%.2f  →  time %+.3f%s",
                    attr, rate, cap, timeFactor, positionNote));
        }

        // Boss sketch for reference.
        double bossTime = BossScalingEngine.computeTimeFactor(playerLevel, cfg.bosses);
        double bossDist = cfg.bosses.affectBosses && cfg.distanceScaling.enabled
                ? BossScalingEngine.computeDistanceFactor(horizontalDistance, cfg.distanceScaling, cfg.bosses)
                : 0.0;
        double bossTotal = BossScalingEngine.combineFactor(bossTime, bossDist, cfg.bosses);
        lines.add(String.format(Locale.ROOT,
                "Boss (if any here): time=%+.3f, distance=%+.3f  →  total %+.3f (cap %.2f)",
                bossTime, bossDist, bossTotal, cfg.bosses.bossMaxFactor));

        return lines;
    }

    static List<String> formatInspect(Mob mob) {
        List<String> lines = new ArrayList<>();
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        EntityType<?> type = mob.getType();
        boolean isBoss = type.is(MobScalingHandler.BOSSES_TAG);
        String toggleKey = MobScalingHandler.resolveToggleKey(typeId);
        Variant variant = detectVariant(mob);

        lines.add(String.format(Locale.ROOT,
                "=== Inspect: %s at (%.1f, %.1f, %.1f) ===",
                typeId != null ? typeId : "unknown",
                mob.getX(), mob.getY(), mob.getZ()));
        lines.add(String.format(Locale.ROOT,
                "Scaling path: %s",
                isBoss ? "boss" : (toggleKey != null ? "vanilla/" + toggleKey : "none")));
        lines.add(String.format(Locale.ROOT,
                "HP: %.1f / %.1f",
                mob.getHealth(), mob.getMaxHealth()));
        lines.add("Variant: " + variant.label());

        double healthFactor = ScalingEngine.readHealthScalingFactor(mob);
        lines.add(String.format(Locale.ROOT,
                "Scaling factor (MAX_HEALTH axes): %+.3f",
                healthFactor));

        // Enumerate every tribulation-namespaced modifier on the mob so
        // an admin can see exactly what was applied. Order is deterministic:
        // attributes in ALL_ATTRIBUTES first (health → toughness), each with
        // its modifiers sorted by ID for stable output.
        String abilities = MobScalingDataCollector.describeAbilities(mob);
        lines.add("Abilities: " + (abilities.isEmpty() ? "(none)" : abilities));

        List<String> modifierLines = collectModifierLines(mob);
        if (modifierLines.isEmpty()) {
            lines.add("Modifiers: (none)");
        } else {
            lines.add("Modifiers:");
            lines.addAll(modifierLines);
        }
        return lines;
    }

    // ---- Small pure helpers ----

    enum Variant {
        NONE("none"),
        BIG("big"),
        SPEED("speed"),
        DEADEYE("deadeye"),
        BRUTE("brute");

        private final String label;

        Variant(String label) { this.label = label; }

        String label() { return label; }
    }

    /**
     * Identify a special variant. Skeleton variants are detected by their
     * per-variant scoreboard tag (always applied, even when a Deadeye's health
     * malus is 0 and it carries no attribute modifier). Zombie variants are
     * detected by their modifier IDs — Big and Speed are mutually exclusive at
     * roll time, but their IDs are distinct so we check both with Big winning on
     * the unlikely chance both are present (keeps output deterministic).
     */
    static Variant detectVariant(Mob mob) {
        if (mob == null) return Variant.NONE;
        if (mob.getTags().contains(SkeletonVariantHandler.DEADEYE_TAG)) {
            return Variant.DEADEYE;
        }
        if (mob.getTags().contains(SkeletonVariantHandler.BRUTE_TAG)) {
            return Variant.BRUTE;
        }
        if (hasModifier(mob, ZombieVariantHandler.BIG_HEALTH_ID)
                || hasModifier(mob, ZombieVariantHandler.BIG_DAMAGE_ID)
                || hasModifier(mob, ZombieVariantHandler.BIG_SIZE_ID)) {
            return Variant.BIG;
        }
        if (hasModifier(mob, ZombieVariantHandler.SPEED_SPEED_ID)
                || hasModifier(mob, ZombieVariantHandler.SPEED_HEALTH_ID)) {
            return Variant.SPEED;
        }
        return Variant.NONE;
    }

    private static boolean hasModifier(Mob mob, ResourceLocation id) {
        for (Map.Entry<String, Holder<Attribute>> entry : ScalingEngine.attributeHolders().entrySet()) {
            AttributeInstance inst = mob.getAttribute(entry.getValue());
            if (inst != null && inst.getModifier(id) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Walk every supported attribute on the mob and collect any modifier whose
     * ID is in the {@code tribulation} namespace. Emits a short
     * {@code "  attribute/modifier-name op=OP amount=+0.123"} line per match.
     */
    private static List<String> collectModifierLines(Mob mob) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Holder<Attribute>> entry : ScalingEngine.attributeHolders().entrySet()) {
            String attrKey = entry.getKey();
            AttributeInstance inst = mob.getAttribute(entry.getValue());
            if (inst == null) continue;
            for (AttributeModifier mod : inst.getModifiers()) {
                ResourceLocation id = mod.id();
                if (id == null) continue;
                if (!Tribulation.MOD_ID.equals(id.getNamespace())) continue;
                out.add(String.format(Locale.ROOT,
                        "  %s / %s : %s %+.4f",
                        attrKey, id.getPath(), opLabel(mod.operation()), mod.amount()));
            }
        }
        return out;
    }

    private static String opLabel(AttributeModifier.Operation op) {
        return switch (op) {
            case ADD_VALUE -> "add";
            case ADD_MULTIPLIED_BASE -> "×base";
            case ADD_MULTIPLIED_TOTAL -> "×total";
        };
    }

    private static double horizontalDistanceFromSpawn(ServerLevel world, double x, double z) {
        var spawn = world.getSharedSpawnPos();
        double dx = x - spawn.getX();
        double dz = z - spawn.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Raycast from the player's eye along their view vector and return the
     * first {@link Mob} within range, or {@code null} if nothing qualifying
     * is hit. Uses {@link ProjectileUtil#getEntityHitResult} so the ray
     * respects entity bounding boxes exactly (not block occlusion — partial
     * wall cover is intentional for debug convenience).
     */
    private static Mob raycastMob(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 view = player.getViewVector(1.0f);
        Vec3 end = eye.add(view.x * range, view.y * range, view.z * range);
        AABB box = player.getBoundingBox().expandTowards(view.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player, eye, end, box,
                e -> e instanceof Mob && !e.isSpectator() && e.isAlive(),
                range * range);
        if (hit == null) return null;
        Entity entity = hit.getEntity();
        return entity instanceof Mob mob ? mob : null;
    }

    static String onOff(boolean value) {
        return value ? "on" : "off";
    }

    /**
     * Render a tick count as a human-readable duration. Used in {@code /info}
     * and {@code /level} output. Falls back to raw ticks when the value is
     * smaller than a second.
     */
    static String formatTicksAsDuration(int ticks) {
        if (ticks < 20) return ticks + "t";
        long totalSeconds = ticks / 20L;
        if (totalSeconds < 60) return totalSeconds + "s";
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes < 60) {
            return seconds == 0
                    ? minutes + "m"
                    : String.format(Locale.ROOT, "%dm%02ds", minutes, seconds);
        }
        long hours = minutes / 60;
        long remMinutes = minutes % 60;
        return remMinutes == 0
                ? hours + "h"
                : String.format(Locale.ROOT, "%dh%02dm", hours, remMinutes);
    }
}
