package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.api.TribulationAPI;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.event.WeaponEquipmentHandler;
import com.rfizzle.tribulation.scaling.ScalingEngine;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WeaponEquipmentGameTest implements FabricGameTest {

    @GameTest(template = "tribulation:empty_3x3")
    public void weapon_equippedAtTier5(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new net.minecraft.core.BlockPos(1, 2, 1));
        mob.getTags().remove(WeaponEquipmentHandler.PROCESSED_TAG);
        mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);

        TribulationConfig cfg = new TribulationConfig();
        cfg.weaponEquipment.enabled = true;
        cfg.weaponEquipment.tiers.get("tier5").wearChancePercent = 100;

        WeaponEquipmentHandler.processWeapon(mob, 5, cfg);

        helper.succeedWhen(() -> {
            ItemStack mainHand = mob.getMainHandItem();
            if (mainHand.isEmpty()) {
                helper.fail("Mob should have weapon equipped at tier 5 with 100% wear chance");
            }
        });
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void weapon_skippedForIncapableMob(GameTestHelper helper) {
        // A creeper can't wield a weapon: the capability guard must leave its hand
        // empty even with a guaranteed wear roll, and mark it processed so the
        // check isn't retried on reload.
        Mob mob = helper.spawnWithNoFreeWill(EntityType.CREEPER, new net.minecraft.core.BlockPos(1, 2, 1));
        mob.getTags().remove(WeaponEquipmentHandler.PROCESSED_TAG);
        mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);

        TribulationConfig cfg = new TribulationConfig();
        cfg.weaponEquipment.enabled = true;
        cfg.weaponEquipment.tiers.get("tier5").wearChancePercent = 100;

        WeaponEquipmentHandler.processWeapon(mob, 5, cfg);

        helper.succeedWhen(() -> {
            if (!mob.getMainHandItem().isEmpty()) {
                helper.fail("Creeper should never be given a weapon");
            }
            if (!mob.getTags().contains(WeaponEquipmentHandler.PROCESSED_TAG)) {
                helper.fail("Skipped mob should still be tagged processed");
            }
        });
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void weapon_keptOnFailedWearRoll(GameTestHelper helper) {
        // A failed wear roll must not disarm a mob that came armed. Vindicators are
        // weapon-capable (pass supportsWeapons) and spawn with an iron axe in vanilla;
        // a 0% wear chance guarantees the roll fails, and the axe must survive.
        Mob mob = helper.spawnWithNoFreeWill(EntityType.VINDICATOR, new net.minecraft.core.BlockPos(1, 2, 1));
        mob.getTags().remove(WeaponEquipmentHandler.PROCESSED_TAG);
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));

        TribulationConfig cfg = new TribulationConfig();
        cfg.weaponEquipment.enabled = true;
        cfg.weaponEquipment.tiers.get("tier1").wearChancePercent = 0;

        WeaponEquipmentHandler.processWeapon(mob, 1, cfg);

        helper.succeedWhen(() -> {
            if (mob.getMainHandItem().getItem() != Items.IRON_AXE) {
                helper.fail("Vindicator must keep its vanilla axe when the wear roll fails, had "
                        + mob.getMainHandItem().getItem());
            }
        });
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void weapon_materialStaysWithinTierPool(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new net.minecraft.core.BlockPos(1, 2, 1));
        mob.getTags().remove(WeaponEquipmentHandler.PROCESSED_TAG);
        mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);

        TribulationConfig cfg = new TribulationConfig();
        cfg.weaponEquipment.enabled = true;
        TribulationConfig.WeaponTier tier1 = cfg.weaponEquipment.tiers.get("tier1");
        tier1.wearChancePercent = 100;

        Set<Item> allowed = new HashSet<>();
        for (var e : tier1.materialWeights.entrySet()) {
            if (e.getValue() <= 0) continue;
            WeaponEquipmentHandler.WeaponMaterial mat = WeaponEquipmentHandler.WeaponMaterial.fromKey(e.getKey());
            if (mat != null) {
                allowed.add(mat.getSword());
                allowed.add(mat.getAxe());
            }
        }

        WeaponEquipmentHandler.processWeapon(mob, 1, cfg);

        helper.succeedWhen(() -> {
            ItemStack stack = mob.getMainHandItem();
            if (stack.isEmpty()) {
                helper.fail("Tier-1 mob with 100% wear should have equipped a weapon");
            }
            if (!allowed.contains(stack.getItem())) {
                helper.fail("Tier-1 mob equipped out-of-pool weapon " + stack.getItem());
            }
        });
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void weapon_damageCeilingClamping(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new net.minecraft.core.BlockPos(1, 2, 1));

        // Clear any damage modifiers
        AttributeInstance damageInst = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        for (String axis : List.of(ScalingEngine.AXIS_TIME, ScalingEngine.AXIS_DISTANCE, ScalingEngine.AXIS_HEIGHT)) {
            damageInst.removeModifier(ScalingEngine.modifierId(axis, ScalingEngine.ATTR_DAMAGE));
        }

        // Equip Netherite Sword (8 damage)
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));

        // Add a large Tribulation modifier (e.g., +200% base damage)
        // Operation.ADD_MULTIPLIED_BASE
        damageInst.addPermanentModifier(new AttributeModifier(
                ScalingEngine.modifierId(ScalingEngine.AXIS_TIME, ScalingEngine.ATTR_DAMAGE),
                2.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));

        final double[] swordDamage = {0};
        mob.getMainHandItem().forEachModifier(EquipmentSlot.MAINHAND, (h, mod) -> {
            if (h.equals(Attributes.ATTACK_DAMAGE) && mod.operation() == AttributeModifier.Operation.ADD_VALUE) {
                swordDamage[0] += mod.amount();
            }
        });

        double baseValue = damageInst.getBaseValue();
        double nonTribTotal = baseValue + swordDamage[0];
        double initialTribValue = nonTribTotal * 2.0;
        double total = nonTribTotal + initialTribValue;
        double ceiling = 20.0;
        double surplus = total - ceiling;
        double expectedTribValue = initialTribValue - surplus;
        double expectedMod = expectedTribValue / nonTribTotal;

        ScalingEngine.clampToCeiling(mob, ScalingEngine.ATTR_DAMAGE, ceiling);

        helper.succeedWhen(() -> {
            AttributeModifier mod = damageInst.getModifier(ScalingEngine.modifierId(ScalingEngine.AXIS_TIME, ScalingEngine.ATTR_DAMAGE));
            if (mod == null) {
                helper.fail("Tribulation modifier missing");
            }

            if (Math.abs(mod.amount() - expectedMod) > 0.01) {
                helper.fail("Damage modifier not scaled correctly: expected " + expectedMod + ", got " + mod.amount() +
                    " (base=" + baseValue + ", sword=" + swordDamage[0] + ", nonTribTotal=" + nonTribTotal + ")");
            }
        });
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void api_weaponDropChanceProvider(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new net.minecraft.core.BlockPos(1, 2, 1));
        ItemStack stack = new ItemStack(Items.IRON_SWORD);

        TribulationAPI.setWeaponDropChanceProvider((m, tier, s, def) -> 0.5f);

        try {
            float chance = TribulationAPI.resolveWeaponDropChance(mob, 1, stack, 0.085f);
            helper.assertValueEqual(chance, 0.5f, "Drop chance should be 0.5f from provider");
        } finally {
            TribulationAPI.setWeaponDropChanceProvider((m, tier, s, def) -> def);
        }
        helper.succeed();
    }
}
