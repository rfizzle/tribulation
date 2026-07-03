package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.champion.ChampionAffix;
import com.rfizzle.tribulation.champion.ChampionManager;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.config.TribulationConfig.Champions;
import com.rfizzle.tribulation.data.TribulationAttachments;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Runtime behavior of champion affixes plus champion death rewards.
 *
 * <ul>
 *   <li><b>Vampiric</b> / <b>Thorns</b> — {@code AFTER_DAMAGE} hooks keyed on
 *       the champion being the direct attacker / victim respectively.</li>
 *   <li><b>Explosive</b> — {@code AFTER_DEATH}: a cosmetic-terrain explosion
 *       ({@code ExplosionInteraction.NONE}) that still damages entities.</li>
 *   <li><b>Regenerating</b> / <b>Knockback aura</b> — a coarse server-tick
 *       pulse that only scans champions near players (an aura or regen far
 *       from any player is unobservable, so the scan stays cheap).</li>
 *   <li><b>Bonus loot</b> — {@code AFTER_DEATH}: extra rolls of the mob's own
 *       loot table (multiplier-style reward; no custom loot tables).</li>
 * </ul>
 */
public final class ChampionEffectHandler {

    /** Regen pulse cadence; {@code regenHealthPerSecond} is applied per pulse. */
    static final int REGEN_INTERVAL_TICKS = 20;
    /** Radius around each player scanned for pulsing champions. */
    private static final double SCAN_RADIUS = 16.0;

    /**
     * The death explosion fires at the tail of {@code LivingEntity.die()},
     * after vanilla has already dropped the champion's loot and XP orbs at the
     * corpse — a plain explosion would vaporize the very reward the champion
     * exists to grant. This calculator spares ground items and orbs while
     * still hurting nearby combatants.
     */
    private static final ExplosionDamageCalculator EXPLOSION_SPARES_DROPS = new ExplosionDamageCalculator() {
        @Override
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return !(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb);
        }
    };

    private ChampionEffectHandler() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register(ChampionEffectHandler::onAfterDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register(ChampionEffectHandler::onAfterDeath);
        ServerTickEvents.END_SERVER_TICK.register(ChampionEffectHandler::onServerTick);
    }

    static void onAfterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
        if (blocked || damageTaken <= 0) return;
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.champions.enabled) return;
        Champions.Affixes affixes = cfg.champions.affixes;

        try {
            // Vampiric: a champion dealing a direct hit heals for a fraction of
            // it. Thorns-typed sources are excluded so a Vampiric champion never
            // heals off damage its victim's Thorned affix reflected for it.
            if (source.getEntity() instanceof Mob attacker
                    && source.getDirectEntity() == attacker
                    && !source.is(DamageTypes.THORNS)
                    && affixes.vampiricHealFraction > 0
                    && ChampionManager.hasAffix(attacker, ChampionAffix.VAMPIRIC)) {
                attacker.heal((float) (damageTaken * affixes.vampiricHealFraction));
            }

            // Thorns: a champion taking a direct hit reflects a fraction back.
            // Skip thorns-typed sources so two thorns champions can't ping-pong.
            if (entity instanceof Mob victim
                    && affixes.thornsFraction > 0
                    && !source.is(DamageTypes.THORNS)
                    && source.getEntity() instanceof LivingEntity attacker
                    && source.getDirectEntity() == attacker
                    && ChampionManager.hasAffix(victim, ChampionAffix.THORNS)) {
                attacker.hurt(victim.damageSources().thorns(victim),
                        (float) (damageTaken * affixes.thornsFraction));
            }
        } catch (Exception e) {
            Tribulation.LOGGER.error("Champion on-hit affix failed on {}", entity, e);
        }
    }

    static void onAfterDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof Mob mob)) return;
        if (!(entity.level() instanceof ServerLevel world)) return;
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.champions.enabled) return;
        if (!ChampionManager.isChampion(mob)) return;

        try {
            if (cfg.champions.affixes.explosivePower > 0
                    && ChampionManager.hasAffix(mob, ChampionAffix.EXPLOSIVE)) {
                world.explode(mob, null, EXPLOSION_SPARES_DROPS,
                        mob.getX(), mob.getY(), mob.getZ(),
                        (float) cfg.champions.affixes.explosivePower,
                        false,
                        Level.ExplosionInteraction.NONE);
            }

            dropBonusLoot(mob, world, source, cfg.champions.bonusLootRolls);
        } catch (Exception e) {
            Tribulation.LOGGER.error("Champion death handling failed on {}", mob, e);
        }
    }

    /**
     * Roll the mob's own loot table {@code rolls} extra times and drop the
     * results at the corpse. Each roll draws fresh from the level RNG, so the
     * bonus rolls aren't copies of the vanilla drop. Gated on the same
     * {@code doMobLoot} gamerule as the vanilla drop it augments.
     */
    private static void dropBonusLoot(Mob mob, ServerLevel world, DamageSource source, int rolls) {
        if (rolls <= 0) return;
        if (!world.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) return;
        ResourceKey<LootTable> tableKey = mob.getLootTable();
        if (tableKey == null) return;
        LootTable table = world.getServer().reloadableRegistries().getLootTable(tableKey);
        if (table == LootTable.EMPTY) return;

        LootParams.Builder builder = new LootParams.Builder(world)
                .withParameter(LootContextParams.THIS_ENTITY, mob)
                .withParameter(LootContextParams.ORIGIN, mob.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity());
        if (source.getEntity() instanceof Player player) {
            builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                    .withLuck(player.getLuck());
        }
        LootParams params = builder.create(LootContextParamSets.ENTITY);

        for (int i = 0; i < rolls; i++) {
            table.getRandomItems(params, mob::spawnAtLocation);
        }
    }

    static void onServerTick(MinecraftServer server) {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.champions.enabled) return;
        Champions.Affixes affixes = cfg.champions.affixes;

        int tick = server.getTickCount();
        int auraInterval = Math.max(1, affixes.knockbackAuraIntervalTicks);
        boolean regenDue = affixes.regenHealthPerSecond > 0 && tick % REGEN_INTERVAL_TICKS == 0;
        boolean auraDue = affixes.knockbackAuraStrength > 0 && tick % auraInterval == 0;
        if (!regenDue && !auraDue) return;

        for (ServerLevel level : server.getAllLevels()) {
            if (level.players().isEmpty()) continue;
            LongSet seen = new LongOpenHashSet();
            for (ServerPlayer player : level.players()) {
                var box = player.getBoundingBox().inflate(SCAN_RADIUS);
                for (Mob mob : level.getEntitiesOfClass(Mob.class, box,
                        m -> m.isAlive() && m.hasAttached(TribulationAttachments.CHAMPION_AFFIXES))) {
                    if (!seen.add(mob.getId())) continue;
                    pulse(mob, level, affixes, regenDue, auraDue);
                }
            }
        }
    }

    private static void pulse(Mob mob, ServerLevel level, Champions.Affixes affixes, boolean regenDue, boolean auraDue) {
        if (regenDue && ChampionManager.hasAffix(mob, ChampionAffix.REGENERATING)
                && mob.getHealth() < mob.getMaxHealth()) {
            mob.heal((float) affixes.regenHealthPerSecond);
        }

        if (auraDue && ChampionManager.hasAffix(mob, ChampionAffix.KNOCKBACK_AURA)) {
            double radius = Math.max(0, affixes.knockbackAuraRadius);
            if (radius <= 0) return;
            var auraBox = mob.getBoundingBox().inflate(radius);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, auraBox,
                    p -> !p.isSpectator() && !p.isCreative() && p.distanceToSqr(mob) <= radius * radius)) {
                // knockback() pushes the target away from the given direction
                // (attacker minus target, as in vanilla melee). hurtMarked makes
                // the server sync the new motion to the player's client.
                player.knockback(affixes.knockbackAuraStrength,
                        mob.getX() - player.getX(), mob.getZ() - player.getZ());
                player.hurtMarked = true;
            }
        }
    }
}
