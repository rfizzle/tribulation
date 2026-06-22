package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.compat.common.MobScalingDataCollector;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.config.TribulationConfig.SpecialSkeletons;
import com.rfizzle.tribulation.event.SkeletonVariantHandler;
import com.rfizzle.tribulation.mixin.AbstractSkeletonAccessor;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;

/**
 * In-game coverage for {@link SkeletonVariantHandler} — modifier application,
 * the per-variant tag stamp, mutual exclusion, and (critically) the bow-cadence
 * override delivered through {@code AbstractSkeletonMixin} after the handler
 * re-invokes {@code reassessWeaponGoal()}.
 *
 * <p>The bow interval lives in {@code RangedBowAttackGoal.attackIntervalMin},
 * which has no getter, so it is read by reflection. Each skeleton is given a bow
 * so the bow goal is the active goal. The mixin reads the live config, so every
 * test forces values on {@link Tribulation#getConfig()} and restores them in a
 * {@code finally} block.
 */
public class SkeletonVariantGameTest implements FabricGameTest {

    @GameTest(template = "tribulation:empty_3x3")
    public void deadeye_appliesTagModifierAndFasterInterval(GameTestHelper helper) {
        AbstractSkeleton sk = spawnBowSkeleton(helper, EntityType.SKELETON);
        TribulationConfig cfg = Tribulation.getConfig();
        SpecialSkeletons sky = cfg.specialSkeletons;
        Snapshot saved = Snapshot.capture(sky);

        try {
            sky.enabled = true;
            sky.deadeyeSkeletonChance = 100;
            sky.bruteSkeletonChance = 0;
            sky.deadeyeSkeletonAttackInterval = 15;

            reset(sk);
            SkeletonVariantHandler.Variant chosen =
                    SkeletonVariantHandler.apply(sk, "skeleton", sky, helper.getLevel().getRandom());

            helper.assertTrue(chosen == SkeletonVariantHandler.Variant.DEADEYE,
                    "deadeye should win at 100% chance, got " + chosen);
            helper.assertTrue(sk.getTags().contains(SkeletonVariantHandler.DEADEYE_TAG),
                    "deadeye tag must be applied");
            helper.assertTrue(hasMod(sk, Attributes.MAX_HEALTH, SkeletonVariantHandler.DEADEYE_HEALTH_ID),
                    "deadeye health malus modifier must be present");
            helper.assertFalse(sk.getTags().contains(SkeletonVariantHandler.BRUTE_TAG),
                    "brute tag must not be applied");
            helper.assertFalse(hasMod(sk, Attributes.MAX_HEALTH, SkeletonVariantHandler.BRUTE_HEALTH_ID),
                    "brute modifiers must be absent");
            assertBowInterval(helper, sk, 15,
                    "deadeye bow interval should match deadeyeSkeletonAttackInterval");
        } finally {
            saved.restore(sky);
        }
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void brute_appliesTagsModifiersAndSlowerInterval(GameTestHelper helper) {
        AbstractSkeleton sk = spawnBowSkeleton(helper, EntityType.SKELETON);
        TribulationConfig cfg = Tribulation.getConfig();
        SpecialSkeletons sky = cfg.specialSkeletons;
        Snapshot saved = Snapshot.capture(sky);

        try {
            sky.enabled = true;
            sky.deadeyeSkeletonChance = 0;
            sky.bruteSkeletonChance = 100;
            sky.bruteSkeletonAttackInterval = 70;
            sky.bruteSkeletonBonusHealth = 10;
            sky.bruteSkeletonBonusKnockbackResistance = 0.5;
            sky.bruteSkeletonSize = 1.3;

            reset(sk);
            SkeletonVariantHandler.Variant chosen =
                    SkeletonVariantHandler.apply(sk, "skeleton", sky, helper.getLevel().getRandom());

            helper.assertTrue(chosen == SkeletonVariantHandler.Variant.BRUTE,
                    "brute should win at 100% chance, got " + chosen);
            helper.assertTrue(sk.getTags().contains(SkeletonVariantHandler.BRUTE_TAG),
                    "brute tag must be applied");
            helper.assertTrue(hasMod(sk, Attributes.MAX_HEALTH, SkeletonVariantHandler.BRUTE_HEALTH_ID),
                    "brute health modifier must be present");
            helper.assertTrue(hasMod(sk, Attributes.KNOCKBACK_RESISTANCE, SkeletonVariantHandler.BRUTE_KNOCKBACK_ID),
                    "brute knockback resistance modifier must be present");
            helper.assertTrue(hasMod(sk, Attributes.SCALE, SkeletonVariantHandler.BRUTE_SIZE_ID),
                    "brute size modifier must be present");
            helper.assertFalse(sk.getTags().contains(SkeletonVariantHandler.DEADEYE_TAG),
                    "deadeye tag must not be applied");
            assertBowInterval(helper, sk, 70,
                    "brute bow interval should match bruteSkeletonAttackInterval");
        } finally {
            saved.restore(sky);
        }
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void rolls_areMutuallyExclusive_deadeyeWins(GameTestHelper helper) {
        AbstractSkeleton sk = spawnBowSkeleton(helper, EntityType.SKELETON);
        TribulationConfig cfg = Tribulation.getConfig();
        SpecialSkeletons sky = cfg.specialSkeletons;
        Snapshot saved = Snapshot.capture(sky);

        try {
            sky.enabled = true;
            sky.deadeyeSkeletonChance = 100;
            sky.bruteSkeletonChance = 100;

            reset(sk);
            SkeletonVariantHandler.Variant chosen =
                    SkeletonVariantHandler.apply(sk, "skeleton", sky, helper.getLevel().getRandom());

            helper.assertTrue(chosen == SkeletonVariantHandler.Variant.DEADEYE,
                    "deadeye must win when both roll at 100%, got " + chosen);
            helper.assertTrue(sk.getTags().contains(SkeletonVariantHandler.DEADEYE_TAG),
                    "deadeye tag must be applied");
            helper.assertFalse(sk.getTags().contains(SkeletonVariantHandler.BRUTE_TAG),
                    "brute tag must not be applied when deadeye wins");
        } finally {
            saved.restore(sky);
        }
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void strayAndBogged_areProcessed(GameTestHelper helper) {
        TribulationConfig cfg = Tribulation.getConfig();
        SpecialSkeletons sky = cfg.specialSkeletons;
        Snapshot saved = Snapshot.capture(sky);

        try {
            sky.enabled = true;
            sky.deadeyeSkeletonChance = 100;
            sky.bruteSkeletonChance = 0;

            AbstractSkeleton stray = spawnBowSkeleton(helper, EntityType.STRAY);
            reset(stray);
            SkeletonVariantHandler.apply(stray, "stray", sky, helper.getLevel().getRandom());
            helper.assertTrue(stray.getTags().contains(SkeletonVariantHandler.PROCESSED_TAG),
                    "stray must be stamped processed");
            helper.assertTrue(stray.getTags().contains(SkeletonVariantHandler.DEADEYE_TAG),
                    "stray must roll deadeye at 100%");

            AbstractSkeleton bogged = spawnBowSkeleton(helper, EntityType.BOGGED);
            reset(bogged);
            SkeletonVariantHandler.apply(bogged, "bogged", sky, helper.getLevel().getRandom());
            helper.assertTrue(bogged.getTags().contains(SkeletonVariantHandler.PROCESSED_TAG),
                    "bogged must be stamped processed");
            helper.assertTrue(bogged.getTags().contains(SkeletonVariantHandler.DEADEYE_TAG),
                    "bogged must roll deadeye at 100%");
        } finally {
            saved.restore(sky);
        }
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void deadeye_withZeroMalus_stillDetectedByTag(GameTestHelper helper) {
        AbstractSkeleton sk = spawnBowSkeleton(helper, EntityType.SKELETON);
        TribulationConfig cfg = Tribulation.getConfig();
        SpecialSkeletons sky = cfg.specialSkeletons;
        Snapshot saved = Snapshot.capture(sky);

        try {
            sky.enabled = true;
            sky.deadeyeSkeletonChance = 100;
            sky.bruteSkeletonChance = 0;
            sky.deadeyeSkeletonMalusHealth = 0;

            reset(sk);
            SkeletonVariantHandler.apply(sk, "skeleton", sky, helper.getLevel().getRandom());

            // With a 0 malus there is no attribute modifier — the per-variant tag is
            // the only detection signal, and the data collector must still report it.
            helper.assertFalse(hasMod(sk, Attributes.MAX_HEALTH, SkeletonVariantHandler.DEADEYE_HEALTH_ID),
                    "no health modifier should exist when the malus is 0");
            helper.assertTrue(sk.getTags().contains(SkeletonVariantHandler.DEADEYE_TAG),
                    "deadeye tag must be present even with a 0 malus");

            CompoundTag data = new CompoundTag();
            MobScalingDataCollector.collect(sk, data);
            helper.assertTrue("deadeye".equals(data.getString(MobScalingDataCollector.KEY_VARIANT)),
                    "data collector must report deadeye by tag, got "
                            + data.getString(MobScalingDataCollector.KEY_VARIANT));
        } finally {
            saved.restore(sky);
        }
        helper.succeed();
    }

    // ---- Helpers ----

    private static AbstractSkeleton spawnBowSkeleton(GameTestHelper helper, EntityType<? extends AbstractSkeleton> type) {
        AbstractSkeleton sk = helper.spawnWithNoFreeWill(type, new BlockPos(1, 2, 1));
        sk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        return sk;
    }

    /**
     * Clear any variant state the spawn-time handler may have applied, so each
     * test starts from a clean skeleton regardless of the default-config roll.
     */
    private static void reset(Mob mob) {
        mob.getTags().remove(SkeletonVariantHandler.PROCESSED_TAG);
        mob.getTags().remove(SkeletonVariantHandler.DEADEYE_TAG);
        mob.getTags().remove(SkeletonVariantHandler.BRUTE_TAG);
        removeMod(mob, Attributes.MAX_HEALTH, SkeletonVariantHandler.DEADEYE_HEALTH_ID);
        removeMod(mob, Attributes.MAX_HEALTH, SkeletonVariantHandler.BRUTE_HEALTH_ID);
        removeMod(mob, Attributes.KNOCKBACK_RESISTANCE, SkeletonVariantHandler.BRUTE_KNOCKBACK_ID);
        removeMod(mob, Attributes.SCALE, SkeletonVariantHandler.BRUTE_SIZE_ID);
    }

    private static void removeMod(Mob mob, Holder<Attribute> attr, ResourceLocation id) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null) inst.removeModifier(id);
    }

    private static boolean hasMod(Mob mob, Holder<Attribute> attr, ResourceLocation id) {
        AttributeInstance inst = mob.getAttribute(attr);
        return inst != null && inst.getModifier(id) != null;
    }

    private static void assertBowInterval(GameTestHelper helper, AbstractSkeleton sk, int expected, String msg) {
        RangedBowAttackGoal<AbstractSkeleton> goal =
                ((AbstractSkeletonAccessor) sk).tribulation$getBowGoal();
        helper.assertTrue(goal != null, "bow goal must exist");
        int actual = readAttackIntervalMin(goal);
        helper.assertTrue(actual == expected,
                msg + " (expected " + expected + ", got " + actual + ")");
    }

    private static int readAttackIntervalMin(RangedBowAttackGoal<?> goal) {
        try {
            Field f = RangedBowAttackGoal.class.getDeclaredField("attackIntervalMin");
            f.setAccessible(true);
            return f.getInt(goal);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not read RangedBowAttackGoal.attackIntervalMin", e);
        }
    }

    private record Snapshot(
            boolean enabled,
            int deadeyeChance, int deadeyeInterval, double deadeyeMalus,
            int bruteChance, int bruteInterval,
            double bruteHealth, double bruteKnockback, double bruteSize) {

        static Snapshot capture(SpecialSkeletons s) {
            return new Snapshot(s.enabled,
                    s.deadeyeSkeletonChance, s.deadeyeSkeletonAttackInterval, s.deadeyeSkeletonMalusHealth,
                    s.bruteSkeletonChance, s.bruteSkeletonAttackInterval,
                    s.bruteSkeletonBonusHealth, s.bruteSkeletonBonusKnockbackResistance, s.bruteSkeletonSize);
        }

        void restore(SpecialSkeletons s) {
            s.enabled = enabled;
            s.deadeyeSkeletonChance = deadeyeChance;
            s.deadeyeSkeletonAttackInterval = deadeyeInterval;
            s.deadeyeSkeletonMalusHealth = deadeyeMalus;
            s.bruteSkeletonChance = bruteChance;
            s.bruteSkeletonAttackInterval = bruteInterval;
            s.bruteSkeletonBonusHealth = bruteHealth;
            s.bruteSkeletonBonusKnockbackResistance = bruteKnockback;
            s.bruteSkeletonSize = bruteSize;
        }
    }
}
