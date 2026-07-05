package com.rfizzle.tribulation.scaling;

import com.rfizzle.tribulation.config.TribulationConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the structure danger-zone level boost for a spawn position (see
 * {@link TribulationConfig.StructureBoosts}), keeping the spawn hot path
 * cheap with a per-world chunk cache.
 *
 * <p>On the first spawn in a chunk, the structure references stored on the
 * chunk (and its neighbors within the margin radius) are resolved to the
 * configured boosts and margin-inflated bounding boxes, and cached as a small
 * {@link BoostZone} array — {@link #NO_ZONES} in the common open-terrain
 * case, so subsequent spawns in that chunk cost one map lookup. A build that
 * could not see every relevant chunk (an unloaded neighbor or start chunk) is
 * used for the current spawn but NOT cached, so it is retried instead of
 * freezing a partial result. Entries are evicted when their chunk unloads and
 * the whole per-world cache is dropped on world unload; a config reload swaps
 * the config object, which invalidates the cache by identity. (Identity is
 * the only invalidation signal — an in-place edit of {@code marginBlocks}
 * would leave stale zone bounds cached, but the only production reload path,
 * {@code Tribulation.reloadConfig()}, always swaps the whole object.)
 *
 * <p>Membership is tested against the structure start's overall bounding box
 * (the danger-zone footprint), not per-piece — corridors and courtyards
 * between pieces count as inside. Only spawn-time position matters; a mob
 * wandering into a structure later is unaffected by design.
 */
public final class StructureBoostManager {

    /**
     * One cached danger zone: a structure start's bounding box already
     * inflated by the configured margin, the boost it grants, and the
     * structure ID for debug display. Bounds are plain ints so the per-spawn
     * containment test is allocation-free.
     */
    public record BoostZone(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                            int boost, ResourceLocation structureId) {

        /**
         * Block-inclusive containment: bounds are inclusive block coordinates
         * (vanilla {@link BoundingBox} convention), so an entity anywhere
         * within the max-edge block — e.g. at {@code maxX + 0.5} — is inside.
         */
        public boolean contains(double x, double y, double z) {
            return x >= minX && x < maxX + 1
                    && y >= minY && y < maxY + 1
                    && z >= minZ && z < maxZ + 1;
        }

        static BoostZone of(BoundingBox box, int margin, int boost, ResourceLocation structureId) {
            return new BoostZone(
                    box.minX() - margin, box.minY() - margin, box.minZ() - margin,
                    box.maxX() + margin, box.maxY() + margin, box.maxZ() + margin,
                    boost, structureId);
        }
    }

    public static final BoostZone[] NO_ZONES = new BoostZone[0];

    /**
     * Per-world chunk→zones cache, tagged with the config object it was built
     * against so a config reload (which swaps the whole config) invalidates it
     * by identity.
     */
    private static final class WorldCache {
        final TribulationConfig cfg;
        // fastutil long-keyed map so the per-spawn lookup never boxes a Long
        // (CLAUDE.md forbids per-spawn allocations on the scaling hot path).
        // Guarded by its own monitor: reads dominate and vanilla accesses it
        // from the server thread only, so the uncontended lock is cheaper than
        // ConcurrentHashMap's boxed keys.
        private final Long2ObjectOpenHashMap<BoostZone[]> chunks = new Long2ObjectOpenHashMap<>();

        WorldCache(TribulationConfig cfg) {
            this.cfg = cfg;
        }

        BoostZone[] get(long key) {
            synchronized (chunks) {
                return chunks.get(key);
            }
        }

        void put(long key, BoostZone[] zones) {
            synchronized (chunks) {
                chunks.put(key, zones);
            }
        }

        void remove(long key) {
            synchronized (chunks) {
                chunks.remove(key);
            }
        }
    }

    private static final Map<ServerLevel, WorldCache> CACHES = new ConcurrentHashMap<>();

    private StructureBoostManager() {}

    public static void register() {
        // Eviction and builds both run on the server thread in vanilla; under
        // parallelized ticking an in-flight build could re-insert an entry
        // just after its unload event, leaving a correct-but-idle entry that
        // the next unload (or world unload / config swap) cleans up.
        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            WorldCache cache = CACHES.get(world);
            if (cache != null) {
                cache.remove(chunk.getPos().toLong());
            }
        });
        ServerWorldEvents.UNLOAD.register((server, world) -> CACHES.remove(world));
    }

    /**
     * The structure boost active at a position — the largest boost among the
     * cached danger zones containing it, or 0 in open terrain. Callers gate on
     * {@link TribulationConfig#hasStructureBoosts()} so an empty config map
     * never reaches this method.
     */
    public static int boostAt(ServerLevel world, double x, double y, double z, TribulationConfig cfg) {
        return maxBoostAt(zonesAt(world, x, z, cfg), x, y, z);
    }

    /**
     * The cached danger zones for the chunk containing (x, z), building the
     * chunk's entry on first use. Exposed (rather than just {@link #boostAt})
     * so the debug/inspect commands can also report which structures match.
     */
    public static BoostZone[] zonesAt(ServerLevel world, double x, double z, TribulationConfig cfg) {
        WorldCache cache = CACHES.get(world);
        if (cache == null || cache.cfg != cfg) {
            // Atomic install so a racing caller still holding the pre-reload
            // config can't overwrite a freshly installed new-generation cache.
            // The capturing lambda only allocates on this rare miss path.
            cache = CACHES.compute(world, (w, existing) ->
                    existing != null && existing.cfg == cfg ? existing : new WorldCache(cfg));
        }
        int chunkX = SectionPos.blockToSectionCoord(Mth.floor(x));
        int chunkZ = SectionPos.blockToSectionCoord(Mth.floor(z));
        long key = ChunkPos.asLong(chunkX, chunkZ);
        // Plain get/build/put rather than computeIfAbsent: the build touches
        // chunk data and must not run while holding the map lock. A racing
        // duplicate build is idempotent and harmless.
        BoostZone[] zones = cache.get(key);
        if (zones == null) {
            Build build = buildZones(world, chunkX, chunkZ, cfg);
            zones = build.zones;
            if (build.complete) {
                cache.put(key, zones);
            }
        }
        return zones;
    }

    /**
     * Drop every cached chunk for a world, forcing rebuilds on the next
     * lookup. Production invalidation is config identity plus chunk-unload
     * eviction; this exists for gametests that stamp synthetic structure
     * starts into chunks whose zones may already be cached.
     */
    public static void invalidate(ServerLevel world) {
        CACHES.remove(world);
    }

    /**
     * Pure core of the per-spawn lookup: the largest boost among the zones
     * containing the position. Unit-testable without a Minecraft bootstrap.
     */
    public static int maxBoostAt(BoostZone[] zones, double x, double y, double z) {
        int best = 0;
        for (BoostZone zone : zones) {
            if (zone.boost() > best && zone.contains(x, y, z)) {
                best = zone.boost();
            }
        }
        return best;
    }

    /**
     * How many neighbor chunks (per axis, each direction) must be scanned for
     * structure references so a margin-inflated bounding box can't be missed:
     * vanilla stores a reference on every chunk a start's box intersects, so a
     * position within {@code marginBlocks} of a box is at most
     * {@code ceil(marginBlocks / 16)} chunks from a referencing chunk. Pure
     * math for unit coverage.
     */
    public static int neighborChunkRadius(int marginBlocks) {
        if (marginBlocks <= 0) return 0;
        return (marginBlocks + 15) >> 4;
    }

    /** A zone build plus whether every relevant chunk was available — only complete builds are cached. */
    private record Build(BoostZone[] zones, boolean complete) {}

    /**
     * Resolve the danger zones affecting one chunk: scan structure references
     * on the chunk and its neighbors within the margin radius, keep starts
     * whose structure carries a configured boost, and store their
     * margin-inflated bounds. Runs once per chunk per cache generation; the
     * common no-structures case returns the shared {@link #NO_ZONES}.
     *
     * <p>Every chunk access uses {@code requireChunk=false} — this must never
     * force-load or generate a chunk from the spawn path. (Notably,
     * {@code StructureManager.fillStartsForStructure} force-loads the start
     * chunk, which is why starts are resolved by hand here.) A chunk that
     * isn't in memory marks the build incomplete so the caller uses the
     * partial result once without caching it.
     */
    private static Build buildZones(ServerLevel world, int chunkX, int chunkZ, TribulationConfig cfg) {
        int margin = cfg.structureBoosts != null ? cfg.structureBoosts.marginBlocks : 0;
        int radius = neighborChunkRadius(margin);
        Registry<Structure> registry = world.registryAccess().registryOrThrow(Registries.STRUCTURE);

        List<BoostZone> zones = null;
        Set<StructureStart> seen = null;
        boolean complete = true;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkAccess chunk = world.getChunk(chunkX + dx, chunkZ + dz, ChunkStatus.STRUCTURE_REFERENCES, false);
                if (chunk == null) {
                    complete = false;
                    continue;
                }

                for (Map.Entry<Structure, LongSet> entry : chunk.getAllReferences().entrySet()) {
                    Structure structure = entry.getKey();
                    int boost = cfg.getStructureBoost(registry.wrapAsHolder(structure));
                    if (boost <= 0) continue;

                    ResourceLocation structureId = registry.getKey(structure);
                    for (LongIterator refs = entry.getValue().iterator(); refs.hasNext(); ) {
                        long ref = refs.nextLong();
                        // A reference points at the start's own chunk, which for
                        // a large structure can sit outside the loaded area.
                        ChunkAccess startChunk = world.getChunk(ChunkPos.getX(ref), ChunkPos.getZ(ref), ChunkStatus.STRUCTURE_STARTS, false);
                        if (startChunk == null) {
                            complete = false;
                            continue;
                        }
                        StructureStart start = startChunk.getStartForStructure(structure);
                        if (start == null || !start.isValid()) continue;
                        if (seen == null) {
                            seen = new HashSet<>();
                            zones = new ArrayList<>();
                        }
                        if (seen.add(start)) {
                            zones.add(BoostZone.of(start.getBoundingBox(), margin, boost, structureId));
                        }
                    }
                }
            }
        }

        BoostZone[] out = zones == null || zones.isEmpty() ? NO_ZONES : zones.toArray(new BoostZone[0]);
        return new Build(out, complete);
    }
}
