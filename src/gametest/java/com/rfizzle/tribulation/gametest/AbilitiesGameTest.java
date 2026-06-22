package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.ability.AbilityManager;
import com.rfizzle.tribulation.config.TribulationConfig;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.monster.Bogged;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.lang.reflect.Method;

/**
 * Deterministic gametest coverage for the tier abilities that hinge on live
 * registry/entity state: arrow effect upgrades (Stray/Bogged), held-crossbow
 * enchantments (Pillager), and the door-breaking difficulty relaxation
 * (Vindicator). Each ability is asserted both enabled and disabled so the
 * config toggle is proven to gate it. Projectile flight and door-pathfinding
 * timing are avoided in favor of inspecting the produced arrow / goal directly.
 */
public class AbilitiesGameTest implements FabricGameTest {

    private static final BlockPos SPAWN = new BlockPos(1, 2, 1);

    // ---- Stray: Slowness II arrows ----

    @GameTest(template = "tribulation:empty_3x3")
    public void stray_slownessArrowsUpgradedAtTier2(GameTestHelper helper) {
        Stray stray = (Stray) helper.spawnWithNoFreeWill(EntityType.STRAY, SPAWN);
        TribulationConfig cfg = new TribulationConfig();
        AbilityManager.applyAbilities(stray, 2, "stray", cfg);

        int amplifier = maxArrowEffectAmplifier(helper, stray, MobEffects.MOVEMENT_SLOWDOWN.value());
        if (amplifier != 1) {
            helper.fail("Tier-2 stray arrow should carry Slowness II (amp 1), got amp " + amplifier);
        }
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void stray_slownessArrowsVanillaWhenDisabled(GameTestHelper helper) {
        Stray stray = (Stray) helper.spawnWithNoFreeWill(EntityType.STRAY, SPAWN);
        TribulationConfig cfg = new TribulationConfig();
        cfg.abilities.straySlownessUpgrade = false;
        AbilityManager.applyAbilities(stray, 2, "stray", cfg);

        int amplifier = maxArrowEffectAmplifier(helper, stray, MobEffects.MOVEMENT_SLOWDOWN.value());
        if (amplifier != 0) {
            helper.fail("Disabled toggle should leave vanilla Slowness I (amp 0), got amp " + amplifier);
        }
        helper.succeed();
    }

    // ---- Bogged: Poison II arrows ----

    @GameTest(template = "tribulation:empty_3x3")
    public void bogged_poisonArrowsUpgradedAtTier2(GameTestHelper helper) {
        Bogged bogged = (Bogged) helper.spawnWithNoFreeWill(EntityType.BOGGED, SPAWN);
        TribulationConfig cfg = new TribulationConfig();
        AbilityManager.applyAbilities(bogged, 2, "bogged", cfg);

        int amplifier = maxArrowEffectAmplifier(helper, bogged, MobEffects.POISON.value());
        if (amplifier != 1) {
            helper.fail("Tier-2 bogged arrow should carry Poison II (amp 1), got amp " + amplifier);
        }
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void bogged_poisonArrowsVanillaWhenDisabled(GameTestHelper helper) {
        Bogged bogged = (Bogged) helper.spawnWithNoFreeWill(EntityType.BOGGED, SPAWN);
        TribulationConfig cfg = new TribulationConfig();
        cfg.abilities.boggedPoisonUpgrade = false;
        AbilityManager.applyAbilities(bogged, 2, "bogged", cfg);

        int amplifier = maxArrowEffectAmplifier(helper, bogged, MobEffects.POISON.value());
        if (amplifier != 0) {
            helper.fail("Disabled toggle should leave vanilla Poison I (amp 0), got amp " + amplifier);
        }
        helper.succeed();
    }

    // ---- Endermite: call sleepers when hurt ----

    @GameTest(template = "tribulation:empty_3x3")
    public void endermite_callsSleepersWhenHurt(GameTestHelper helper) {
        helper.getLevel().getGameRules()
                .getRule(GameRules.RULE_MOBGRIEFING).set(true, helper.getLevel().getServer());

        // An infested block one tile below the endermite reverts to its host
        // (stone) when the ability summons a silverfish from it.
        BlockPos infested = new BlockPos(1, 1, 1);
        helper.setBlock(infested, Blocks.INFESTED_STONE);

        Endermite mite = (Endermite) helper.spawnWithNoFreeWill(EntityType.ENDERMITE, new BlockPos(1, 2, 1));
        TribulationConfig cfg = new TribulationConfig();
        AbilityManager.applyAbilities(mite, 2, "endermite", cfg);

        // AFTER_DAMAGE fires synchronously inside hurt(), so the summon runs now.
        mite.hurt(helper.getLevel().damageSources().generic(), 1.0f);

        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.STONE, infested));
    }

    // ---- Pillager: Quick Charge (T2) / Multishot (T4) ----

    @GameTest(template = "tribulation:empty_3x3")
    public void pillager_quickChargeAtTier2(GameTestHelper helper) {
        Mob pillager = helper.spawnWithNoFreeWill(EntityType.PILLAGER, SPAWN);
        pillager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        TribulationConfig cfg = new TribulationConfig();
        AbilityManager.applyAbilities(pillager, 2, "pillager", cfg);

        if (enchantLevel(helper, pillager, Enchantments.QUICK_CHARGE) <= 0) {
            helper.fail("Tier-2 pillager crossbow should have Quick Charge");
        }
        // Multishot is a tier-4 ability; it must not appear at tier 2.
        if (enchantLevel(helper, pillager, Enchantments.MULTISHOT) > 0) {
            helper.fail("Tier-2 pillager crossbow should NOT have Multishot yet");
        }
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void pillager_multishotAtTier4(GameTestHelper helper) {
        Mob pillager = helper.spawnWithNoFreeWill(EntityType.PILLAGER, SPAWN);
        pillager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        TribulationConfig cfg = new TribulationConfig();
        AbilityManager.applyAbilities(pillager, 4, "pillager", cfg);

        if (enchantLevel(helper, pillager, Enchantments.MULTISHOT) <= 0) {
            helper.fail("Tier-4 pillager crossbow should have Multishot");
        }
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void pillager_crossbowUnenchantedWhenDisabled(GameTestHelper helper) {
        Mob pillager = helper.spawnWithNoFreeWill(EntityType.PILLAGER, SPAWN);
        pillager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        TribulationConfig cfg = new TribulationConfig();
        cfg.abilities.pillagerQuickCharge = false;
        cfg.abilities.pillagerMultishot = false;
        AbilityManager.applyAbilities(pillager, 4, "pillager", cfg);

        if (enchantLevel(helper, pillager, Enchantments.QUICK_CHARGE) > 0
                || enchantLevel(helper, pillager, Enchantments.MULTISHOT) > 0) {
            helper.fail("Disabled toggles should leave the pillager crossbow unenchanted");
        }
        helper.succeed();
    }

    // ---- Witch / Ravager / Guardian: tier tags (also forces the mixins on
    //      these classes to apply, validating their injection points) ----

    @GameTest(template = "tribulation:empty_3x3")
    public void witch_abilityTagsByTier(GameTestHelper helper) {
        Mob witch = helper.spawnWithNoFreeWill(EntityType.WITCH, SPAWN);
        TribulationConfig cfg = new TribulationConfig();
        AbilityManager.applyAbilities(witch, 5, "witch", cfg);
        if (!witch.getTags().contains(AbilityManager.TAG_LINGERING_POTIONS)) {
            helper.fail("Tier-5 witch should have lingering potions (tier-3 ability)");
        }
        if (!witch.getTags().contains(AbilityManager.TAG_AGGRESSIVE_HEALING)) {
            helper.fail("Tier-5 witch should have aggressive healing");
        }

        Mob lowWitch = helper.spawnWithNoFreeWill(EntityType.WITCH, new BlockPos(2, 2, 2));
        AbilityManager.applyAbilities(lowWitch, 2, "witch", cfg);
        if (lowWitch.getTags().contains(AbilityManager.TAG_LINGERING_POTIONS)
                || lowWitch.getTags().contains(AbilityManager.TAG_AGGRESSIVE_HEALING)) {
            helper.fail("Tier-2 witch should have no tier-3/5 witch abilities");
        }
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void ravager_roarTaggedAtTier3(GameTestHelper helper) {
        Mob ravager = helper.spawnWithNoFreeWill(EntityType.RAVAGER, SPAWN);
        TribulationConfig cfg = new TribulationConfig();
        AbilityManager.applyAbilities(ravager, 3, "ravager", cfg);
        if (!ravager.getTags().contains(AbilityManager.TAG_RAVAGER_ROAR)) {
            helper.fail("Tier-3 ravager should have expanded roar");
        }
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void guardian_beamTaggedAtTier3(GameTestHelper helper) {
        Mob guardian = helper.spawnWithNoFreeWill(EntityType.GUARDIAN, SPAWN);
        TribulationConfig cfg = new TribulationConfig();
        AbilityManager.applyAbilities(guardian, 3, "guardian", cfg);
        if (!guardian.getTags().contains(AbilityManager.TAG_GUARDIAN_BEAM)) {
            helper.fail("Tier-3 guardian should have faster beam");
        }
        helper.succeed();
    }

    // ---- Vindicator: door breaking on any difficulty ----

    @GameTest(template = "tribulation:empty_3x3")
    public void vindicator_breaksDoorsOnAnyDifficultyAtTier3(GameTestHelper helper) {
        Vindicator vindicator = (Vindicator) helper.spawnWithNoFreeWill(EntityType.VINDICATOR, SPAWN);
        TribulationConfig cfg = new TribulationConfig();
        AbilityManager.applyAbilities(vindicator, 3, "vindicator", cfg);

        // A goal whose own predicate rejects every difficulty: only the tier tag
        // (via BreakDoorGoalMixin) can make it valid on NORMAL.
        BreakDoorGoal goal = new BreakDoorGoal(vindicator, difficulty -> false);
        if (!isValidDifficulty(helper, goal, Difficulty.NORMAL)) {
            helper.fail("Tier-3 vindicator should break doors on NORMAL difficulty");
        }
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void vindicator_doorBreakingGatedBelowTier3(GameTestHelper helper) {
        Vindicator vindicator = (Vindicator) helper.spawnWithNoFreeWill(EntityType.VINDICATOR, SPAWN);
        TribulationConfig cfg = new TribulationConfig();
        AbilityManager.applyAbilities(vindicator, 1, "vindicator", cfg);

        BreakDoorGoal goal = new BreakDoorGoal(vindicator, difficulty -> false);
        if (isValidDifficulty(helper, goal, Difficulty.NORMAL)) {
            helper.fail("Tier-1 vindicator should not break doors on NORMAL difficulty");
        }
        helper.succeed();
    }

    // ---- Helpers ----

    /**
     * Invoke the mob's protected {@code getArrow} and return the highest
     * amplifier of {@code effect} among the produced arrow's stored effects, or
     * {@code -1} if absent. Reflection is used because {@code getArrow} is
     * protected; gametests run with named mappings so the name resolves.
     */
    private static int maxArrowEffectAmplifier(GameTestHelper helper, Mob shooter,
                                               net.minecraft.world.effect.MobEffect effect) {
        try {
            // Vanilla signature is getArrow(arrowStack, velocity, weaponStack).
            ItemStack arrowStack = new ItemStack(Items.ARROW);
            ItemStack bow = new ItemStack(Items.BOW);
            Method getArrow = shooter.getClass().getDeclaredMethod(
                    "getArrow", ItemStack.class, float.class, ItemStack.class);
            getArrow.setAccessible(true);
            Object arrow = getArrow.invoke(shooter, arrowStack, 1.0f, bow);

            Method getPotionContents = arrow.getClass().getDeclaredMethod("getPotionContents");
            getPotionContents.setAccessible(true);
            PotionContents contents = (PotionContents) getPotionContents.invoke(arrow);

            int max = -1;
            for (MobEffectInstance instance : contents.customEffects()) {
                if (instance.getEffect().value() == effect) {
                    max = Math.max(max, instance.getAmplifier());
                }
            }
            return max;
        } catch (java.lang.reflect.InvocationTargetException e) {
            helper.fail("getArrow/getPotionContents threw: " + e.getCause());
            return -1;
        } catch (ReflectiveOperationException e) {
            helper.fail("Reflection on getArrow/getPotionContents failed: " + e);
            return -1;
        }
    }

    private static int enchantLevel(GameTestHelper helper, Mob mob,
                                    net.minecraft.resources.ResourceKey<Enchantment> key) {
        Holder<Enchantment> holder = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        return EnchantmentHelper.getItemEnchantmentLevel(holder, mob.getMainHandItem());
    }

    private static boolean isValidDifficulty(GameTestHelper helper, BreakDoorGoal goal, Difficulty difficulty) {
        try {
            Method method = BreakDoorGoal.class.getDeclaredMethod("isValidDifficulty", Difficulty.class);
            method.setAccessible(true);
            return (boolean) method.invoke(goal, difficulty);
        } catch (ReflectiveOperationException e) {
            helper.fail("Reflection on isValidDifficulty failed: " + e);
            return false;
        }
    }
}
