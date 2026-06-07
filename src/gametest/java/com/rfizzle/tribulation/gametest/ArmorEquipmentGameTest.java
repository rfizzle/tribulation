package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.api.TribulationAPI;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.scaling.ScalingEngine;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.OptionalInt;

public class ArmorEquipmentGameTest implements FabricGameTest {

    @GameTest(template = "tribulation:empty_3x3")
    public void armor_equippedAtTier5(GameTestHelper helper) {
        // We use a high tier to ensure high probability of armor
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new net.minecraft.core.BlockPos(1, 2, 1));

        // Manual override for testing
        TribulationConfig cfg = new TribulationConfig();
        cfg.armorEquipment.enabled = true;
        cfg.armorEquipment.tiers.get("tier5").wearChancePercent = 100;
        cfg.armorEquipment.tiers.get("tier5").slotCoveragePercent = 100;

        com.rfizzle.tribulation.event.ArmorEquipmentHandler.processArmor(mob, 5, cfg);

        helper.succeedWhen(() -> {
            boolean hasAnyArmor = !mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty() ||
                                 !mob.getItemBySlot(EquipmentSlot.CHEST).isEmpty() ||
                                 !mob.getItemBySlot(EquipmentSlot.LEGS).isEmpty() ||
                                 !mob.getItemBySlot(EquipmentSlot.FEET).isEmpty();
            if (!hasAnyArmor) {
                helper.fail("Mob should have armor equipped at tier 5 with 100% wear chance");
            }
        });
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void armor_ceilingClamping(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new net.minecraft.core.BlockPos(1, 2, 1));

        // Equip full netherite (20 armor)
        mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        mob.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));

        // Add a large Tribulation modifier (e.g., +10 armor)
        ScalingEngine.applyAttributeModifiers(mob, ScalingEngine.ATTR_ARMOR,
            new com.rfizzle.tribulation.scaling.ScalingResult.AttributeFactor(10.0, 0, 0, 10.0));

        // Total would be 20 (base items) + 10 (trib) + 2 (zombie base) = 32
        // Ceiling is 24
        ScalingEngine.clampToCeiling(mob, ScalingEngine.ATTR_ARMOR, 24.0);

        helper.succeedWhen(() -> {
            double val = mob.getAttributeValue(Attributes.ARMOR);
            if (val > 24.001) {
                helper.fail("Armor value " + val + " exceeds ceiling 24.0");
            }
            // Netherite items must stay, so total must be at least 20 (items) + 2 (zombie base) = 22
            if (val < 22.0) {
                 helper.fail("Armor value " + val + " should not be lower than base + items (22.0)");
            }
        });
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void api_frozenTierPersistence(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new net.minecraft.core.BlockPos(1, 2, 1));
        int testTier = 3;
        mob.setAttached(com.rfizzle.tribulation.data.TribulationAttachments.SCALED_TIER, testTier);

        OptionalInt tier = TribulationAPI.getScaledTier(mob);
        helper.assertTrue(tier.isPresent(), "Scaled tier should be present");
        helper.assertValueEqual(tier.getAsInt(), testTier, "Scaled tier should be 3");
        helper.assertTrue(TribulationAPI.wasScaledByTribulation(mob), "wasScaledByTribulation should be true");

        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void api_armorDropChanceProvider(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new net.minecraft.core.BlockPos(1, 2, 1));
        ItemStack stack = new ItemStack(Items.IRON_CHESTPLATE);

        TribulationAPI.setArmorDropChanceProvider((m, tier, slot, s, def) -> 0.5f);

        float chance = TribulationAPI.resolveArmorDropChance(mob, 1, EquipmentSlot.CHEST, stack, 0.085f);
        helper.assertValueEqual(chance, 0.5f, "Drop chance should be 0.5f from provider");

        // Restore default
        TribulationAPI.setArmorDropChanceProvider((m, tier, slot, s, def) -> def);
        helper.succeed();
    }
}
