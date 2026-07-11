package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.BloodMoonState;
import com.rfizzle.tribulation.network.TribulationNetworking;
import com.rfizzle.tribulation.sound.TribulationSounds;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Drives the Blood Moon event: rolls once per full-moon nightfall in the
 * Overworld, and while the event is active amplifies the moon scaling axis
 * (read by {@link com.rfizzle.tribulation.scaling.ScalingEngine}), raises
 * hostile spawn caps (read by the spawn-cap mixins), blocks sleeping — denying
 * new bed attempts and waking anyone already asleep — and keeps clients in
 * sync for the red-sky visuals and warning sting.
 *
 * <p>The live flag is mirrored into a static {@code volatile} so the two hot
 * paths — per-spawn scaling and the natural-spawner cap checks — never touch
 * {@link BloodMoonState}'s SavedData lookup. The mirror is rebuilt from the
 * persisted state on server start, so a restart mid-event resumes the night.
 */
public final class BloodMoonHandler {
    private static final int CHECK_INTERVAL = 20;
    public static final int FULL_MOON_PHASE = 0;

    private static volatile boolean active;

    private BloodMoonHandler() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                active = BloodMoonState.getOrCreate(server).isActive());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> active = false);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            boolean scheduled = server.getTickCount() % CHECK_INTERVAL == 0;
            if (!scheduled && !active) return;
            TribulationConfig cfg = Tribulation.getConfig();
            if (cfg == null) return;
            try {
                guardSleep(server, cfg);
                if (scheduled) tick(server, cfg);
            } catch (Exception e) {
                Tribulation.LOGGER.error("Blood moon pass failed", e);
            }
        });

        EntitySleepEvents.ALLOW_SLEEPING.register((player, sleepingPos) -> {
            TribulationConfig cfg = Tribulation.getConfig();
            if (cfg == null || !cfg.bloodMoon.blockSleep) return null;
            if (!(player.level() instanceof ServerLevel level) || !isActive(level)) return null;
            player.displayClientMessage(noSleepMessage(), true);
            return Player.BedSleepingProblem.OTHER_PROBLEM;
        });
    }

    private static Component noSleepMessage() {
        return Component.translatable("message.tribulation.blood_moon_no_sleep")
                .withStyle(ChatFormatting.DARK_RED);
    }

    /**
     * The per-tick eviction pass: while the event is active, eject any sleeper
     * (see {@link #wakeSleepers}). Public so gametests can drive it directly.
     */
    public static void guardSleep(MinecraftServer server, TribulationConfig cfg) {
        if (!active) return;
        wakeSleepers(server, cfg);
    }

    /**
     * Eject every sleeping Overworld player with the same no-sleep feedback the
     * bed-attempt denial sends, so the ejection reads as the event's doing.
     * Runs at event start and every server tick while the event is active: the
     * {@code ALLOW_SLEEPING} listener only gates <em>new</em> bed attempts, so
     * a player asleep before the event began — or admitted while
     * {@code blockSleep} was off and the config then reloaded — is invisible
     * to it. Vanilla's night skip reads the deep-sleep counter at the top of
     * each server tick and this eviction runs at the end of every tick, so no
     * sleeper survives a tick boundary and the skip can never fire during the
     * event — even a sleeper already at 99 of the 100 required ticks when
     * {@code blockSleep} flips on is woken before the next read.
     */
    private static void wakeSleepers(MinecraftServer server, TribulationConfig cfg) {
        if (!cfg.bloodMoon.blockSleep) return;
        for (ServerPlayer player : server.overworld().players()) {
            if (!player.isSleeping()) continue;
            player.stopSleepInBed(true, true);
            player.displayClientMessage(noSleepMessage(), true);
        }
    }

    /**
     * True when a Blood Moon is running and applies to the given level. The
     * event exists only in the Overworld — dimensions without a daylight cycle
     * are out of scope by design.
     */
    public static boolean isActive(ServerLevel level) {
        return active && level.dimension() == Level.OVERWORLD;
    }

    /**
     * Moon-axis multiplier the scaling engine applies at the given level:
     * {@code moonBonusMultiplier} during an active Blood Moon, else 1.0.
     */
    public static double moonMultiplier(ServerLevel level, TribulationConfig cfg) {
        if (cfg == null || !cfg.bloodMoon.enabled || !isActive(level)) return 1.0;
        return cfg.bloodMoon.moonBonusMultiplier;
    }

    /**
     * True when the natural-spawner cap mixins should raise the MONSTER cap in
     * the given level.
     */
    public static boolean isSpawnBoostActive(ServerLevel level) {
        TribulationConfig cfg = Tribulation.getConfig();
        return cfg != null && cfg.bloodMoon.enabled && isActive(level);
    }

    /** Current spawn-cap multiplier; 1.0 when the config is unavailable. */
    public static double spawnCapMultiplier() {
        TribulationConfig cfg = Tribulation.getConfig();
        return cfg != null ? cfg.bloodMoon.spawnCapMultiplier : 1.0;
    }

    /**
     * Scale a mob-cap value for a Blood Moon night. Pure {@code int} math so
     * the mixin arithmetic is unit-testable: no-op when the boost is off, and
     * never scales below the vanilla base.
     */
    public static int scaledMobCap(int base, boolean boostActive, double multiplier) {
        if (!boostActive || multiplier <= 1.0) return base;
        return Math.max(base, (int) Math.round(base * multiplier));
    }

    /**
     * Whether this check owes a nightfall roll: feature off-cycle states
     * (already active, daytime, wrong phase) and already-rolled nights all
     * decline. Pure so the trigger conditions are unit-testable.
     */
    public static boolean rollDue(boolean alreadyActive, boolean isNight, int moonPhase, long day, long lastRolledDay) {
        return !alreadyActive && isNight && moonPhase == FULL_MOON_PHASE && day != lastRolledDay;
    }

    /** The event ends exactly when day breaks while it is active. Pure. */
    public static boolean shouldEnd(boolean alreadyActive, boolean isNight) {
        return alreadyActive && !isNight;
    }

    /** One scheduler pass; public so gametests can drive transitions directly. */
    public static void tick(MinecraftServer server, TribulationConfig cfg) {
        ServerLevel overworld = server.overworld();
        BloodMoonState state = BloodMoonState.getOrCreate(server);

        if (!cfg.bloodMoon.enabled) {
            if (state.isActive()) {
                end(server, state, cfg);
            }
            return;
        }

        boolean isNight = !overworld.isDay();
        long day = overworld.getDayTime() / 24000L;

        if (shouldEnd(state.isActive(), isNight)) {
            end(server, state, cfg);
            return;
        }

        if (rollDue(state.isActive(), isNight, overworld.getMoonPhase(), day, state.getLastRolledDay())) {
            state.markRolled(day);
            if (overworld.getRandom().nextDouble() < cfg.bloodMoon.chance) {
                start(server, state, cfg);
            }
        }
    }

    /** Begin the event now (also the {@code /tribulation bloodmoon start} path). */
    public static void start(MinecraftServer server, BloodMoonState state, TribulationConfig cfg) {
        state.setActive(true);
        // Spend tonight's roll no matter how the event began, so an admin
        // stopping a command-started event on a full-moon night isn't
        // immediately re-rolled back into one by the scheduler.
        state.markRolled(server.overworld().getDayTime() / 24000L);
        active = true;
        broadcast(server, cfg);
        wakeSleepers(server, cfg);
        for (ServerPlayer player : server.overworld().players()) {
            player.sendSystemMessage(Component.translatable("message.tribulation.blood_moon_rises")
                    .withStyle(ChatFormatting.DARK_RED));
            if (cfg.bloodMoon.clientEffects) {
                player.connection.send(new ClientboundSoundPacket(
                        TribulationSounds.BLOOD_MOON_WARNING,
                        SoundSource.AMBIENT,
                        player.getX(), player.getY(), player.getZ(),
                        1.0f, 1.0f,
                        player.getRandom().nextLong()));
            }
        }
    }

    /** End the event now (dawn, disable, or {@code /tribulation bloodmoon stop}). */
    public static void end(MinecraftServer server, BloodMoonState state, TribulationConfig cfg) {
        state.setActive(false);
        active = false;
        broadcast(server, cfg);
    }

    /**
     * Push the current tint flag to every connected player. Clients are told
     * "inactive" when {@code clientEffects} is off, so the visuals honor the
     * server's toggle without any client-side config read.
     */
    public static void broadcast(MinecraftServer server, TribulationConfig cfg) {
        boolean visible = syncedStateFor(cfg);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TribulationNetworking.syncBloodMoon(player, visible);
        }
    }

    /** The join-sync view of the flag, mirroring {@link #broadcast}'s gating. */
    public static boolean syncedStateFor(TribulationConfig cfg) {
        return active && cfg != null && cfg.bloodMoon.enabled && cfg.bloodMoon.clientEffects;
    }
}
