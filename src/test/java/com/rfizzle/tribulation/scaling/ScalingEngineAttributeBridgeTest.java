// Tier: 2 (fabric-loader-junit)
package com.rfizzle.tribulation.scaling;

import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.config.TribulationConfig.MobScaling;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bridge between the pure-math {@link ScalingEngine} results and the actual
 * vanilla {@link AttributeMap} produced by an entity's AttributeSupplier.
 *
 * <p>Pure-JUnit tests prove the math; this proves the translation from math →
 * attribute modifier → final {@code getValue()} matches the design expectation
 * (e.g. a zombie at level 250 actually reaches 70 HP, not some other number).
 * Uses the real supplier from {@link Zombie#createAttributes()} so a vanilla
 * change to zombie base stats would fail this test loudly.
 *
 * <p>fabric-loader-junit sets up the Knot classloader (mixins + AWs apply) but
 * does not call {@link Bootstrap#bootStrap()} or invoke mod entrypoints — so
 * any test that touches {@code Attributes} still needs an explicit bootstrap
 * call. No registry unfreeze is required because we only read, not register.
 */
class ScalingEngineAttributeBridgeTest {

    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final double EPS = 1e-9;

    @Test
    void vanillaZombieBaseMaxHealthIsTwenty() {
        AttributeMap attrs = new AttributeMap(Zombie.createAttributes().build());
        assertEquals(20.0, attrs.getBaseValue(Attributes.MAX_HEALTH), EPS);
    }

    @Test
    void healthTimeModifier_atDesignMaxLevel_yieldsSeventyHp() {
        AttributeMap attrs = new AttributeMap(Zombie.createAttributes().build());
        TribulationConfig cfg = new TribulationConfig();
        MobScaling zombie = cfg.scaling.get("zombie");

        // Pure math: at level 250, time factor is capped at healthCap (2.5).
        ScalingResult.AttributeFactor f = ScalingEngine.computeAttributeFactor(
                ScalingEngine.ATTR_HEALTH, 250, 0.0, 0.0, zombie, cfg.statCaps);
        assertEquals(2.5, f.timeFactor(), EPS);

        // Apply with the same ID + operation ScalingEngine uses internally.
        AttributeInstance hp = attrs.getInstance(Attributes.MAX_HEALTH);
        hp.addPermanentModifier(new AttributeModifier(
                ScalingEngine.modifierId(ScalingEngine.AXIS_TIME, ScalingEngine.ATTR_HEALTH),
                f.timeFactor(),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));

        // ADD_MULTIPLIED_BASE: 20 * (1 + 2.5) = 70 HP. Matches DESIGN.md zombie table.
        assertEquals(70.0, hp.getValue(), EPS);
    }

    @Test
    void armorTimeModifier_atDesignMaxLevel_addsFlatEightPoints() {
        AttributeMap attrs = new AttributeMap(Zombie.createAttributes().build());
        TribulationConfig cfg = new TribulationConfig();
        MobScaling zombie = cfg.scaling.get("zombie");

        // Armor uses ADD_VALUE semantics: time factor is in absolute armor points.
        ScalingResult.AttributeFactor f = ScalingEngine.computeAttributeFactor(
                ScalingEngine.ATTR_ARMOR, 250, 0.0, 0.0, zombie, cfg.statCaps);
        assertEquals(8.0, f.timeFactor(), EPS);

        double baseArmor = attrs.getBaseValue(Attributes.ARMOR);
        AttributeInstance armor = attrs.getInstance(Attributes.ARMOR);
        armor.addPermanentModifier(new AttributeModifier(
                ScalingEngine.modifierId(ScalingEngine.AXIS_TIME, ScalingEngine.ATTR_ARMOR),
                f.timeFactor(),
                AttributeModifier.Operation.ADD_VALUE));

        assertEquals(baseArmor + 8.0, armor.getValue(), EPS);
    }

    @Test
    void allThreeAxes_compound_asAddMultipliedBaseSum() {
        AttributeMap attrs = new AttributeMap(Zombie.createAttributes().build());
        TribulationConfig cfg = new TribulationConfig();
        MobScaling zombie = cfg.scaling.get("zombie");

        // Level 100 + distance factor 0.5 + height factor 0.2 = time 1.0, dist 0.5, height 0.2.
        ScalingResult.AttributeFactor f = ScalingEngine.computeAttributeFactor(
                ScalingEngine.ATTR_HEALTH, 100, 0.5, 0.2, zombie, cfg.statCaps);

        AttributeInstance hp = attrs.getInstance(Attributes.MAX_HEALTH);
        hp.addPermanentModifier(new AttributeModifier(
                ScalingEngine.modifierId(ScalingEngine.AXIS_TIME, ScalingEngine.ATTR_HEALTH),
                f.timeFactor(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        hp.addPermanentModifier(new AttributeModifier(
                ScalingEngine.modifierId(ScalingEngine.AXIS_DISTANCE, ScalingEngine.ATTR_HEALTH),
                f.distanceFactor(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        hp.addPermanentModifier(new AttributeModifier(
                ScalingEngine.modifierId(ScalingEngine.AXIS_HEIGHT, ScalingEngine.ATTR_HEALTH),
                f.heightFactor(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE));

        // 20 * (1 + 1.0 + 0.5 + 0.2) = 54.
        assertEquals(54.0, hp.getValue(), EPS);
    }
}
