package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.scaling.ScalingEngine;
import com.rfizzle.tribulation.scaling.TierManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Pillager;

/**
 * Scales pillager-patrol size with the tier of the targeted player(s). When a
 * patrol captain (a {@link Pillager} with {@code isPatrolLeader() == true})
 * loads, the captain's effective tier is resolved by proximity and extra
 * members are spawned according to {@link TribulationConfig.RaidScaling}.
 *
 * <p>The extras are spawned <em>on the next tick</em> rather than inside the
 * load callback, so the world's entity list is never mutated mid-iteration.
 * Each extra is tagged only {@link #PATROL_PROCESSED_TAG} (so this listener
 * skips it) and is deliberately left <em>without</em>
 * {@link MobScalingHandler#PROCESSED_TAG} — that is what lets the natural
 * {@code ENTITY_LOAD} scaling path give it tier-appropriate stats, armor, and
 * weapons like any other spawn.
 *
 * <p>Raider stats/armor/weapons are not handled here: raid mobs are already in
 * the scaling map and flow through {@link MobScalingHandler}. Extra raid waves
 * live in {@code RaidScalingMixin}.
 */
public final class RaidScalingHandler {
    /** Marks a patrol captain as already processed (and tags spawned extras). */
    public static final String PATROL_PROCESSED_TAG = "tribulation_patrol_processed";

    private RaidScalingHandler() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register(RaidScalingHandler::onEntityLoad);
    }

    static void onEntityLoad(Entity entity, ServerLevel world) {
        if (!(entity instanceof Pillager captain)) return;
        if (!captain.isPatrolLeader()) return;
        if (captain.getTags().contains(PATROL_PROCESSED_TAG)) return;

        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || cfg.raidScaling == null || !cfg.raidScaling.enabled) return;

        // Mark the captain now so a chunk reload never re-spawns the extras.
        captain.addTag(PATROL_PROCESSED_TAG);

        try {
            int playerLevel = ScalingEngine.getEffectiveLevel(captain, world);
            spawnExtraMembers(captain, world, playerLevel, cfg);
        } catch (Exception e) {
            Tribulation.LOGGER.error("Failed to scale patrol for {}", captain, e);
        }
    }

    /**
     * Spawn the configured number of extra patrol members for {@code captain}
     * at the supplied effective player level. The count comes from
     * {@link TribulationConfig.RaidScaling#extraPatrolMembers(int)}. The add is
     * deferred to the next server tick so it never mutates the entity list
     * mid-iteration; the captain is re-checked for liveness when the task runs.
     *
     * <p>Exposed (and decoupled from the {@code ENTITY_LOAD}/{@code
     * isPatrolLeader} gate) so gametests can drive it directly with an explicit
     * level, mirroring {@link MobScalingHandler#applyScaling}.
     */
    public static void spawnExtraMembers(Pillager captain, ServerLevel world, int playerLevel, TribulationConfig cfg) {
        if (cfg.raidScaling == null) return;
        int tier = TierManager.getTier(playerLevel, cfg.tiers);
        int extra = cfg.raidScaling.extraPatrolMembers(tier);
        if (extra <= 0) return;

        MinecraftServer server = world.getServer();
        if (server == null) return;

        server.tell(new TickTask(server.getTickCount() + 1, () -> {
            if (!captain.isAlive()) return;
            for (int i = 0; i < extra; i++) {
                spawnOne(captain, world, i);
            }
        }));
    }

    private static void spawnOne(Pillager captain, ServerLevel world, int index) {
        Pillager extra = EntityType.PILLAGER.create(world);
        if (extra == null) return;

        // Fan the extras out slightly so they don't stack on the captain.
        double offset = 0.5 + index;
        extra.moveTo(captain.getX() + offset, captain.getY(), captain.getZ() + offset,
                captain.getYRot(), captain.getXRot());

        // Equip the crossbow / loadout vanilla patrol members get, so the extra
        // behaves like a real patroller rather than a bare-handed pillager.
        extra.finalizeSpawn(world, world.getCurrentDifficultyAt(extra.blockPosition()),
                MobSpawnType.PATROL, null);

        // Migrate with the group rather than standing idle.
        extra.setPatrolTarget(captain.getPatrolTarget());

        // Our own tag (this listener skips it; it is a non-leader anyway). It is
        // deliberately NOT given MobScalingHandler.PROCESSED_TAG so the ENTITY_LOAD
        // scaling hook gives it tier-appropriate stats/armor/weapons on add.
        extra.addTag(PATROL_PROCESSED_TAG);

        world.addFreshEntityWithPassengers(extra);
    }
}
