package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.data.TribulationAttachments;
import com.rfizzle.tribulation.event.PackTacticsHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;

/**
 * Shared-aggro coverage for pack tactics: hurting an eligible mob at or above
 * the tier threshold retargets same-type neighbors onto the attacker, while
 * below-threshold, disabled, ineligible-type, and cross-type cases stay fully
 * vanilla. The damage hook is exercised through
 * {@link PackTacticsHandler#handle} with an injected config (small alert
 * radius, so parallel test structures can't interfere), plus one end-to-end
 * case that routes through a real {@code hurt()} call and the Fabric
 * AFTER_DAMAGE event.
 */
public class PackTacticsGameTest implements FabricGameTest {

    private static final BlockPos VICTIM_POS = new BlockPos(1, 2, 1);
    private static final BlockPos PACKMATE_POS = new BlockPos(2, 2, 1);

    private static TribulationConfig cfg() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.packTactics.alertRadius = 3.0;
        return cfg;
    }

    private ServerPlayer attacker(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos abs = helper.absolutePos(new BlockPos(1, 2, 2));
        player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
        return player;
    }

    private static DamageSource playerAttack(GameTestHelper helper, ServerPlayer player) {
        return helper.getLevel().damageSources().playerAttack(player);
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void packmateAcquiresTargetAboveThreshold(GameTestHelper helper) {
        Mob victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, VICTIM_POS);
        Mob packmate = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, PACKMATE_POS);
        ServerPlayer player = attacker(helper);
        victim.setAttached(TribulationAttachments.SCALED_TIER, 3);

        PackTacticsHandler.handle(victim, playerAttack(helper, player), cfg());

        if (packmate.getTarget() != player) {
            helper.fail("Same-type packmate should target the attacker at tier 3");
        }
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void packmateStaysVanillaBelowThreshold(GameTestHelper helper) {
        Mob victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, VICTIM_POS);
        Mob packmate = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, PACKMATE_POS);
        ServerPlayer player = attacker(helper);
        victim.setAttached(TribulationAttachments.SCALED_TIER, 2);

        PackTacticsHandler.handle(victim, playerAttack(helper, player), cfg());

        if (packmate.getTarget() != null) {
            helper.fail("Below the tier threshold no packmate may be alerted");
        }
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void masterToggleDisablesSharedAggro(GameTestHelper helper) {
        Mob victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, VICTIM_POS);
        Mob packmate = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, PACKMATE_POS);
        ServerPlayer player = attacker(helper);
        victim.setAttached(TribulationAttachments.SCALED_TIER, 5);
        TribulationConfig cfg = cfg();
        cfg.packTactics.enabled = false;

        PackTacticsHandler.handle(victim, playerAttack(helper, player), cfg);

        if (packmate.getTarget() != null) {
            helper.fail("Disabled master toggle must leave targeting vanilla");
        }
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void crossTypeMobIsNotAlerted(GameTestHelper helper) {
        Mob victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, VICTIM_POS);
        Mob bystander = helper.spawnWithNoFreeWill(EntityType.SKELETON, PACKMATE_POS);
        ServerPlayer player = attacker(helper);
        victim.setAttached(TribulationAttachments.SCALED_TIER, 5);

        PackTacticsHandler.handle(victim, playerAttack(helper, player), cfg());

        if (bystander.getTarget() != null) {
            helper.fail("Cross-type mobs must not share aggro");
        }
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void ineligibleTypeIsNotAlerted(GameTestHelper helper) {
        Mob victim = helper.spawnWithNoFreeWill(EntityType.CREEPER, VICTIM_POS);
        Mob packmate = helper.spawnWithNoFreeWill(EntityType.CREEPER, PACKMATE_POS);
        ServerPlayer player = attacker(helper);
        victim.setAttached(TribulationAttachments.SCALED_TIER, 5);

        PackTacticsHandler.handle(victim, playerAttack(helper, player), cfg());

        if (packmate.getTarget() != null) {
            helper.fail("Creepers are not in the default eligible-mob list");
        }
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void engagedPackmateKeepsItsTarget(GameTestHelper helper) {
        Mob victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, VICTIM_POS);
        Mob packmate = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, PACKMATE_POS);
        ServerPlayer player = attacker(helper);
        ServerPlayer other = attacker(helper);
        victim.setAttached(TribulationAttachments.SCALED_TIER, 5);
        packmate.setTarget(other);

        PackTacticsHandler.handle(victim, playerAttack(helper, player), cfg());

        if (packmate.getTarget() != other) {
            helper.fail("A packmate already fighting a living target must not be retargeted");
        }
        player.discard();
        other.discard();
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void realDamageEventAlertsPack(GameTestHelper helper) {
        Mob victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, VICTIM_POS);
        Mob packmate = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, PACKMATE_POS);
        ServerPlayer player = attacker(helper);
        victim.setAttached(TribulationAttachments.SCALED_TIER, 5);

        // Narrow the live config's radius for the duration of the synchronous
        // hurt() so the alert sweep cannot reach parallel test structures.
        // Safe: gametests and the damage hook both run on the server thread.
        TribulationConfig live = com.rfizzle.tribulation.Tribulation.getConfig();
        double savedRadius = live.packTactics.alertRadius;
        live.packTactics.alertRadius = 3.0;
        try {
            victim.hurt(playerAttack(helper, player), 2.0f);
        } finally {
            live.packTactics.alertRadius = savedRadius;
        }

        if (packmate.getTarget() != player) {
            helper.fail("AFTER_DAMAGE from a real hurt() should alert the pack");
        }
        player.discard();
        helper.succeed();
    }

    // ---- spawn-group bonus (runtime coverage of the mixin's supplier path) ----

    @GameTest(template = "tribulation:empty_3x3")
    public void spawnGroupBonusFollowsTierAndEligibility(GameTestHelper helper) {
        ServerPlayer player = attacker(helper);
        TribulationConfig cfg = com.rfizzle.tribulation.Tribulation.getConfig();
        PlayerDifficultyState state =
                PlayerDifficultyState.getOrCreate(helper.getLevel().getServer());
        int savedLevel = state.getLevel(player.getUUID());
        BlockPos pos = helper.absolutePos(VICTIM_POS);
        try {
            state.setLevel(player.getUUID(), cfg.tiers.tier5, cfg.general.maxLevel);
            if (PackTacticsHandler.spawnGroupBonus(EntityType.ZOMBIE, helper.getLevel(), pos)
                    != cfg.packTactics.groupSizeBonus) {
                helper.fail("Eligible type at tier 5 should get the configured group bonus");
            }
            if (PackTacticsHandler.spawnGroupBonus(EntityType.CREEPER, helper.getLevel(), pos) != 0) {
                helper.fail("Ineligible type must never get a group bonus");
            }

            state.setLevel(player.getUUID(), 0, cfg.general.maxLevel);
            if (PackTacticsHandler.spawnGroupBonus(EntityType.ZOMBIE, helper.getLevel(),
                    pos.above()) != 0) {
                helper.fail("Below the tier threshold spawn groups must stay vanilla");
            }
        } finally {
            state.setLevel(player.getUUID(), savedLevel, cfg.general.maxLevel);
        }
        player.discard();
        helper.succeed();
    }
}
