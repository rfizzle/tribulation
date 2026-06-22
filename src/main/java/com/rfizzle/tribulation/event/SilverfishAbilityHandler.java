package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.ability.AbilityManager;
import com.rfizzle.tribulation.config.TribulationConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tier-2 "call sleepers" ability for the {@link Endermite}: when a tagged
 * endermite is hurt, it wakes silverfish from nearby infested blocks — the
 * behavior vanilla gives silverfish through {@code SilverfishWakeUpFriendsGoal}
 * but that endermites have no goal for. The infested-block scan is replicated
 * here: each infested block within {@link #SCAN_RADIUS} is reverted to its host
 * state and a silverfish is spawned in its place.
 *
 * <p>Hooked on {@link ServerLivingEntityEvents#AFTER_DAMAGE}, so the summon
 * fires only when the endermite actually took a hit.
 *
 * <p>Tagged silverfish need no handler: their vanilla goal already does this.
 */
public final class SilverfishAbilityHandler {

    /** Cube half-extent scanned around the endermite for infested blocks. */
    static final int SCAN_RADIUS = 3;
    /** One friend woken per hit, matching the vanilla silverfish cadence. */
    static final int MAX_SUMMONS = 1;

    private SilverfishAbilityHandler() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register(SilverfishAbilityHandler::onAfterDamage);
    }

    static void onAfterDamage(LivingEntity entity, DamageSource source,
                              float baseDamageTaken, float damageTaken, boolean blocked) {
        if (!(entity instanceof Endermite endermite)) return;
        if (!(endermite.level() instanceof ServerLevel level)) return;
        if (!endermite.getTags().contains(AbilityManager.TAG_CALL_SLEEPERS)) return;

        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.abilities.silverfishCallSleepers) return;

        try {
            callSleepers(level, endermite.blockPosition());
        } catch (Exception e) {
            Tribulation.LOGGER.warn("Error calling sleepers for endermite {}", endermite, e);
        }
    }

    /**
     * Summon up to {@link #MAX_SUMMONS} silverfish from infested blocks around
     * {@code center}. Gated by the {@code mobGriefing} game rule, since it alters
     * world blocks — matching how the vanilla wake-friends goal behaves.
     */
    static void callSleepers(ServerLevel level, BlockPos center) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        int summoned = 0;
        for (BlockPos scan : BlockPos.betweenClosed(
                center.offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS),
                center.offset(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS))) {
            BlockState state = level.getBlockState(scan);
            if (state.getBlock() instanceof InfestedBlock infested) {
                BlockPos pos = scan.immutable();
                level.setBlockAndUpdate(pos, infested.hostStateByInfested(state));
                level.levelEvent(2001, pos, Block.getId(state));
                EntityType.SILVERFISH.spawn(level, pos, MobSpawnType.MOB_SUMMONED);
                if (++summoned >= MAX_SUMMONS) break;
            }
        }
    }
}
