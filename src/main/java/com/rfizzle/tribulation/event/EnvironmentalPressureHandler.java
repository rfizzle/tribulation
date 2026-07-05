package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.config.TribulationConfig.EnvironmentalPressure.DebilitatingStrikes;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.data.TribulationAttachments;
import com.rfizzle.tribulation.network.EnvironmentalPressurePayload;
import com.rfizzle.tribulation.scaling.TierManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tier-gated environmental pressure: the world pushes back on high-level
 * players. Both effects gate on the <em>victim player's own</em> tier (from
 * their stored level), never on other players nearby, so mixed-level servers
 * pressure each player individually.
 *
 * <p><b>Debilitating strikes</b> — hooked on
 * {@link ServerLivingEntityEvents#AFTER_DAMAGE}, so there is no per-tick cost;
 * a disabled feature costs one boolean check per damage event. A landed,
 * unblocked melee hit ({@code mob_attack} damage types only — projectiles,
 * explosions, beams, and thorns are all excluded by type) from a
 * Tribulation-scaled hostile applies the configured Weakness/Slowness to the
 * player.
 *
 * <p><b>Oppressive nights</b> — the server computes a per-player darkness
 * strength and syncs it only when it changes (piggybacked on the existing
 * level syncs, which already fire on join, level-ups, decay, relief, shards,
 * and admin commands). Rendering rules live client-side in
 * {@code EnvironmentalPressureClientEffects}.
 */
public final class EnvironmentalPressureHandler {

    /**
     * Last darkness strength sent per player, so the periodic level sync only
     * emits a pressure packet on actual change. Entries are dropped on
     * disconnect; concurrent map because join/disconnect callbacks and the
     * tick loop may touch it from different threads during shutdown.
     */
    private static final Map<UUID, Float> LAST_SENT_NIGHT_DARKNESS = new ConcurrentHashMap<>();

    private EnvironmentalPressureHandler() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register(EnvironmentalPressureHandler::onAfterDamage);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                LAST_SENT_NIGHT_DARKNESS.remove(handler.getPlayer().getUUID()));
    }

    static void onAfterDamage(LivingEntity entity, DamageSource source,
                              float baseDamageTaken, float damageTaken, boolean blocked) {
        if (!(entity instanceof ServerPlayer player)) return;
        // Cheap shape check ahead of the level lookup: the overwhelmingly
        // common player damage events (fall, fire, drowning) have no mob
        // attacker and can bail before touching the difficulty state.
        if (!(source.getEntity() instanceof Mob)) return;
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.environmentalPressure.enabled
                || !cfg.environmentalPressure.debilitatingStrikes.enabled) {
            return;
        }
        if (blocked || damageTaken <= 0f) return;
        int level = PlayerDifficultyState.getOrCreate(player.server).getLevel(player.getUUID());
        handleStrike(player, source, TierManager.getTier(level, cfg.tiers), cfg);
    }

    /**
     * Config- and tier-injected core of the strike hook for a landed,
     * unblocked damage event, exercised directly by gametests.
     */
    public static void handleStrike(ServerPlayer player, DamageSource source, int playerTier, TribulationConfig cfg) {
        if (cfg == null || !cfg.environmentalPressure.strikesActiveAtTier(playerTier)) return;
        // Melee only, filtered positively by damage type: source topology
        // alone cannot prove a melee hit, because creeper explosions, warden
        // sonic booms, and guardian beams also report the mob as both causing
        // and direct entity. The type filter also excludes projectiles and
        // thorns reflections.
        if (!source.is(DamageTypes.MOB_ATTACK) && !source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)) return;
        if (!(source.getEntity() instanceof Mob attacker) || source.getDirectEntity() != attacker) return;
        // Only hostiles Tribulation actually scaled apply pressure.
        if (attacker.getAttached(TribulationAttachments.SCALED_TIER) == null) return;
        try {
            applyStrikeEffects(player, attacker, cfg.environmentalPressure.debilitatingStrikes);
        } catch (Exception e) {
            Tribulation.LOGGER.warn("Error applying debilitating strike from {}", attacker, e);
        }
    }

    private static void applyStrikeEffects(ServerPlayer player, Mob attacker, DebilitatingStrikes strikes) {
        if (strikes.applyWeakness) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                    strikes.weaknessDurationTicks, strikes.weaknessAmplifier), attacker);
        }
        if (strikes.applySlowness) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    strikes.slownessDurationTicks, strikes.slownessAmplifier), attacker);
        }
    }

    /**
     * Push the player's oppressive-nights darkness strength to their client
     * if it changed since the last send. Called from every level sync, so any
     * path that can change the player's tier (or a config reload broadcast)
     * refreshes the client within one sync. A fresh client defaults to 0, so
     * an initial 0 is not sent. Returns whether a payload was sent — the
     * dedup contract is asserted by gametests.
     */
    public static boolean syncNightPressure(ServerPlayer player, TribulationConfig cfg, int level) {
        float darkness = cfg == null ? 0f
                : (float) cfg.environmentalPressure.nightDarknessAtTier(TierManager.getTier(level, cfg.tiers));
        Float last = LAST_SENT_NIGHT_DARKNESS.get(player.getUUID());
        if (last == null ? darkness == 0f : last == darkness) return false;
        ServerPlayNetworking.send(player, new EnvironmentalPressurePayload(darkness));
        LAST_SENT_NIGHT_DARKNESS.put(player.getUUID(), darkness);
        return true;
    }

    /**
     * Re-evaluate and resync every online player's night pressure against the
     * given config. Called after {@code /tribulation reload} so a toggled
     * feature takes effect immediately instead of on the next level sync.
     */
    public static void broadcast(MinecraftServer server, TribulationConfig cfg) {
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncNightPressure(player, cfg, state.getLevel(player.getUUID()));
        }
    }
}
