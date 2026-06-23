package com.rfizzle.tribulation.client;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.TribulationAttachments;
import com.rfizzle.tribulation.event.ZombieVariantHandler;
import com.rfizzle.tribulation.particle.ThreatCue;
import com.rfizzle.tribulation.particle.TribulationParticles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Client-only emitter for threat-telegraphing particles. Each client tick it
 * scans mobs within a fixed radius of the local player and probabilistically
 * spawns a subtle cue on those that are scaled (tier-4+ by default) or carry a
 * Big/Speed zombie variant. Nothing here touches the server: eligibility is
 * read from the synced {@link TribulationAttachments#SCALED_TIER} attachment
 * and from vanilla-synced attribute modifiers. The cue decision itself is the
 * pure {@link ThreatCue#decide} table.
 *
 * <p>The radius is a fixed constant (not config) so the issue's "three knobs"
 * (master toggle, minimum tier, frequency) stays accurate.
 */
public final class TribulationParticleEmitter {
    /** Scan radius around the local player, in blocks (AC: visible within ~16). */
    private static final double RADIUS = 16.0;
    private static final double RADIUS_SQR = RADIUS * RADIUS;

    private TribulationParticleEmitter() {}

    /** Register the per-tick scan on the client tick event. */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(TribulationParticleEmitter::onClientTick);
    }

    private static void onClientTick(Minecraft client) {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.threatParticles.enabled) return;
        if (client.isPaused()) return;

        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level == null || player == null) return;

        int minimumTier = Math.max(0, cfg.threatParticles.minimumTier);
        int frequency = Math.max(1, cfg.threatParticles.particleFrequencyTicks);
        RandomSource random = level.random;

        AABB box = player.getBoundingBox().inflate(RADIUS);
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, box,
                mob -> mob.hasAttached(TribulationAttachments.SCALED_TIER));

        for (Mob mob : mobs) {
            if (mob.distanceToSqr(player) > RADIUS_SQR) continue;

            Integer tierObj = mob.getAttached(TribulationAttachments.SCALED_TIER);
            int tier = tierObj == null ? 0 : tierObj;
            ThreatCue.Type cue = ThreatCue.decide(true, mob.isInvisible(), true,
                    detectVariant(mob), tier, minimumTier);
            if (cue == ThreatCue.Type.NONE) continue;

            // ~1/frequency chance per tick keeps emission sparse and cheap.
            if (random.nextInt(frequency) != 0) continue;
            emit(level, mob, cue, random);
        }
    }

    /**
     * Mirror of {@code TribulationCommand#detectVariant}'s zombie OR-logic,
     * restricted to client-syncable attributes. {@code ATTACK_DAMAGE} (the
     * Big variant's {@code BIG_DAMAGE_ID}) is not synced to clients, so it is
     * deliberately omitted; size ({@code SCALE}), speed ({@code MOVEMENT_SPEED})
     * and health ({@code MAX_HEALTH}) all arrive via vanilla's attribute packet.
     */
    static ThreatCue.Variant detectVariant(Mob mob) {
        if (hasModifier(mob, Attributes.SCALE, ZombieVariantHandler.BIG_SIZE_ID)
                || hasModifier(mob, Attributes.MAX_HEALTH, ZombieVariantHandler.BIG_HEALTH_ID)) {
            return ThreatCue.Variant.BIG;
        }
        if (hasModifier(mob, Attributes.MOVEMENT_SPEED, ZombieVariantHandler.SPEED_SPEED_ID)
                || hasModifier(mob, Attributes.MAX_HEALTH, ZombieVariantHandler.SPEED_HEALTH_ID)) {
            return ThreatCue.Variant.SPEED;
        }
        return ThreatCue.Variant.NONE;
    }

    private static boolean hasModifier(Mob mob, Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance instance = mob.getAttribute(attribute);
        return instance != null && instance.getModifier(id) != null;
    }

    private static void emit(ClientLevel level, Mob mob, ThreatCue.Type cue, RandomSource random) {
        SimpleParticleType type = particleFor(cue);
        if (type == null) return;

        double width = mob.getBbWidth();
        double height = mob.getBbHeight();
        double x = mob.getX() + (random.nextDouble() - 0.5) * width;
        double z = mob.getZ() + (random.nextDouble() - 0.5) * width;

        // Big cue hugs the ground (mass); speed cue trails at mid-body with a
        // little horizontal drift; tier cue rises from the upper body.
        double y;
        double xd = 0.0;
        double yd = 0.0;
        double zd = 0.0;
        switch (cue) {
            case BIG -> y = mob.getY() + random.nextDouble() * 0.3 * height;
            case SPEED -> {
                y = mob.getY() + (0.4 + random.nextDouble() * 0.4) * height;
                xd = (random.nextDouble() - 0.5) * 0.06;
                zd = (random.nextDouble() - 0.5) * 0.06;
            }
            default -> {
                y = mob.getY() + (0.5 + random.nextDouble() * 0.5) * height;
                yd = 0.01;
            }
        }
        level.addParticle(type, x, y, z, xd, yd, zd);
    }

    private static SimpleParticleType particleFor(ThreatCue.Type cue) {
        return switch (cue) {
            case TIER -> TribulationParticles.THREAT_TIER;
            case BIG -> TribulationParticles.THREAT_BIG;
            case SPEED -> TribulationParticles.THREAT_SPEED;
            case NONE -> null;
        };
    }
}
