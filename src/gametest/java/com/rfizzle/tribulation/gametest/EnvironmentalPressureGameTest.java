package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.data.TribulationAttachments;
import com.rfizzle.tribulation.event.EnvironmentalPressureHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;

/**
 * Environmental-pressure coverage. Debilitating strikes: a landed melee hit
 * from a scaled hostile applies the configured effects to a player at or
 * above the tier threshold, while below-threshold, disabled, projectile,
 * explosion, thorns, and unscaled-attacker cases leave the player untouched.
 * The damage hook is exercised through
 * {@link EnvironmentalPressureHandler#handleStrike} with an injected config
 * and tier, plus one end-to-end case that routes through a real
 * {@code hurt()} call and the Fabric AFTER_DAMAGE event. Oppressive nights:
 * the send-only-on-change dedup contract of
 * {@link EnvironmentalPressureHandler#syncNightPressure}, including the
 * initial-zero suppression and per-player gating.
 */
public class EnvironmentalPressureGameTest implements FabricGameTest {

    private static final BlockPos MOB_POS = new BlockPos(1, 2, 1);

    private static TribulationConfig cfg() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.environmentalPressure.enabled = true;
        return cfg;
    }

    private ServerPlayer victim(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos abs = helper.absolutePos(new BlockPos(1, 2, 2));
        player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
        return player;
    }

    private static Mob scaledZombie(GameTestHelper helper) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, MOB_POS);
        zombie.setAttached(TribulationAttachments.SCALED_TIER, 3);
        return zombie;
    }

    private static DamageSource meleeFrom(GameTestHelper helper, Mob attacker) {
        return helper.getLevel().damageSources().mobAttack(attacker);
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void strikeAppliesWeaknessAboveThreshold(GameTestHelper helper) {
        ServerPlayer player = victim(helper);
        Mob zombie = scaledZombie(helper);

        EnvironmentalPressureHandler.handleStrike(player, meleeFrom(helper, zombie), 3, cfg());

        MobEffectInstance effect = player.getEffect(MobEffects.WEAKNESS);
        if (effect == null) {
            helper.fail("A melee hit from a scaled hostile at tier 3 must apply Weakness");
        } else if (effect.getDuration() != 100 || effect.getAmplifier() != 0) {
            helper.fail("Weakness must use the configured duration/amplifier, got "
                    + effect.getDuration() + "/" + effect.getAmplifier());
        }
        if (player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
            helper.fail("Slowness is off by default and must not be applied");
        }
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void strikeBelowThresholdAppliesNothing(GameTestHelper helper) {
        ServerPlayer player = victim(helper);
        Mob zombie = scaledZombie(helper);

        EnvironmentalPressureHandler.handleStrike(player, meleeFrom(helper, zombie), 2, cfg());

        if (player.hasEffect(MobEffects.WEAKNESS)) {
            helper.fail("Below the tier threshold no effect may be applied");
        }
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void masterToggleDisablesStrikes(GameTestHelper helper) {
        ServerPlayer player = victim(helper);
        Mob zombie = scaledZombie(helper);
        TribulationConfig cfg = cfg();
        cfg.environmentalPressure.enabled = false;

        EnvironmentalPressureHandler.handleStrike(player, meleeFrom(helper, zombie), 5, cfg);

        if (player.hasEffect(MobEffects.WEAKNESS)) {
            helper.fail("The master toggle must disable debilitating strikes");
        }
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void projectileHitAppliesNothing(GameTestHelper helper) {
        ServerPlayer player = victim(helper);
        Mob zombie = scaledZombie(helper);
        Arrow arrow = helper.spawn(EntityType.ARROW, MOB_POS);
        DamageSource ranged = helper.getLevel().damageSources().arrow(arrow, zombie);

        EnvironmentalPressureHandler.handleStrike(player, ranged, 5, cfg());

        if (player.hasEffect(MobEffects.WEAKNESS)) {
            helper.fail("Ranged damage sources are out of scope and must not debilitate");
        }
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void explosionAppliesNothing(GameTestHelper helper) {
        ServerPlayer player = victim(helper);
        Mob zombie = scaledZombie(helper);
        // A creeper-style explosion reports the mob as both causing and
        // direct entity — topology alone looks like melee, so this pins the
        // damage-type filter.
        DamageSource blast = helper.getLevel().damageSources().explosion(zombie, zombie);

        EnvironmentalPressureHandler.handleStrike(player, blast, 5, cfg());

        if (player.hasEffect(MobEffects.WEAKNESS)) {
            helper.fail("Explosions are not melee hits and must not debilitate");
        }
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void thornsReflectionAppliesNothing(GameTestHelper helper) {
        ServerPlayer player = victim(helper);
        Mob zombie = scaledZombie(helper);
        DamageSource thorns = helper.getLevel().damageSources().thorns(zombie);

        EnvironmentalPressureHandler.handleStrike(player, thorns, 5, cfg());

        if (player.hasEffect(MobEffects.WEAKNESS)) {
            helper.fail("Thorns reflections must not count as debilitating melee hits");
        }
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void unscaledAttackerAppliesNothing(GameTestHelper helper) {
        ServerPlayer player = victim(helper);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, MOB_POS);
        // The live MobScalingHandler scales the zombie on spawn; strip the
        // attachment so this models a mob Tribulation never touched.
        zombie.removeAttached(TribulationAttachments.SCALED_TIER);

        EnvironmentalPressureHandler.handleStrike(player, meleeFrom(helper, zombie), 5, cfg());

        if (player.hasEffect(MobEffects.WEAKNESS)) {
            helper.fail("Only Tribulation-scaled hostiles may apply debilitating strikes");
        }
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void slownessToggleAppliesConfiguredEffect(GameTestHelper helper) {
        ServerPlayer player = victim(helper);
        Mob zombie = scaledZombie(helper);
        TribulationConfig cfg = cfg();
        cfg.environmentalPressure.debilitatingStrikes.applyWeakness = false;
        cfg.environmentalPressure.debilitatingStrikes.applySlowness = true;
        cfg.environmentalPressure.debilitatingStrikes.slownessDurationTicks = 60;
        cfg.environmentalPressure.debilitatingStrikes.slownessAmplifier = 1;

        EnvironmentalPressureHandler.handleStrike(player, meleeFrom(helper, zombie), 5, cfg);

        MobEffectInstance effect = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (effect == null) {
            helper.fail("applySlowness must apply Slowness on hit");
        } else if (effect.getDuration() != 60 || effect.getAmplifier() != 1) {
            helper.fail("Slowness must use the configured duration/amplifier, got "
                    + effect.getDuration() + "/" + effect.getAmplifier());
        }
        if (player.hasEffect(MobEffects.WEAKNESS)) {
            helper.fail("applyWeakness=false must suppress Weakness");
        }
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void pressureGatesPerPlayer(GameTestHelper helper) {
        ServerPlayer veteran = victim(helper);
        ServerPlayer novice = victim(helper);
        Mob zombie = scaledZombie(helper);
        TribulationConfig cfg = cfg();

        EnvironmentalPressureHandler.handleStrike(veteran, meleeFrom(helper, zombie), 4, cfg);
        EnvironmentalPressureHandler.handleStrike(novice, meleeFrom(helper, zombie), 0, cfg);

        if (!veteran.hasEffect(MobEffects.WEAKNESS)) {
            helper.fail("The high-tier player must feel the pressure");
        }
        if (novice.hasEffect(MobEffects.WEAKNESS)) {
            helper.fail("A low-tier player on the same server must be unaffected");
        }
        veteran.discard();
        novice.discard();
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void realDamageEventAppliesWeakness(GameTestHelper helper) {
        ServerPlayer player = victim(helper);
        Mob zombie = scaledZombie(helper);

        // A freshly placed mock player carries 60 ticks of join
        // invulnerability (ServerPlayer#spawnInvulnerableTime) that would
        // swallow the hit; zero it reflectively rather than idling the test.
        try {
            java.lang.reflect.Field field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            field.setAccessible(true);
            field.setInt(player, 0);
        } catch (ReflectiveOperationException e) {
            helper.fail("ServerPlayer.spawnInvulnerableTime not found — field renamed? " + e);
            return;
        }

        // Route through a real hurt() so the Fabric AFTER_DAMAGE hook and the
        // stored-level tier resolution are exercised. The live config and the
        // player's stored level are restored afterwards.
        TribulationConfig live = Tribulation.getConfig();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(helper.getLevel().getServer());
        boolean savedEnabled = live.environmentalPressure.enabled;
        int savedLevel = state.getLevel(player.getUUID());
        try {
            live.environmentalPressure.enabled = true;
            state.setLevel(player.getUUID(), live.tiers.tier3, live.general.maxLevel);
            player.hurt(meleeFrom(helper, zombie), 2.0f);
        } finally {
            live.environmentalPressure.enabled = savedEnabled;
            state.setLevel(player.getUUID(), savedLevel, live.general.maxLevel);
        }

        if (!player.hasEffect(MobEffects.WEAKNESS)) {
            helper.fail("AFTER_DAMAGE from a real hurt() should apply the strike effect");
        }
        player.discard();
        helper.succeed();
    }

    // ---- oppressive-nights sync (dedup contract of syncNightPressure) ----

    @GameTest(template = "tribulation:empty_3x3")
    public void nightPressureSyncSendsOnlyOnChange(GameTestHelper helper) {
        ServerPlayer player = victim(helper);
        TribulationConfig cfg = cfg();
        int aboveTier = cfg.tiers.tier4;

        if (!EnvironmentalPressureHandler.syncNightPressure(player, cfg, aboveTier)) {
            helper.fail("Crossing the nights threshold must send a pressure payload");
        }
        if (EnvironmentalPressureHandler.syncNightPressure(player, cfg, aboveTier)) {
            helper.fail("An unchanged darkness value must not be re-sent");
        }
        if (!EnvironmentalPressureHandler.syncNightPressure(player, cfg, 0)) {
            helper.fail("Dropping below the threshold must send the zero payload");
        }
        if (EnvironmentalPressureHandler.syncNightPressure(player, cfg, 0)) {
            helper.fail("An unchanged zero must not be re-sent");
        }
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void nightPressureSyncGatesPerPlayer(GameTestHelper helper) {
        ServerPlayer veteran = victim(helper);
        ServerPlayer novice = victim(helper);
        TribulationConfig cfg = cfg();

        if (!EnvironmentalPressureHandler.syncNightPressure(veteran, cfg, cfg.tiers.tier5)) {
            helper.fail("The high-tier player must receive night pressure");
        }
        if (EnvironmentalPressureHandler.syncNightPressure(novice, cfg, 0)) {
            helper.fail("A fresh low-tier player must not receive an initial zero payload");
        }
        veteran.discard();
        novice.discard();
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void nightPressureSyncSuppressedWhenDisabled(GameTestHelper helper) {
        ServerPlayer player = victim(helper);
        TribulationConfig cfg = new TribulationConfig();
        // Master toggle off (the default) — even a max-tier player gets nothing.

        if (EnvironmentalPressureHandler.syncNightPressure(player, cfg, cfg.tiers.tier5)) {
            helper.fail("A disabled feature must not send night pressure to anyone");
        }
        player.discard();
        helper.succeed();
    }
}
