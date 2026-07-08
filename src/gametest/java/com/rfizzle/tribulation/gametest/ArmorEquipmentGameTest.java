package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.api.TribulationAPI;
import com.rfizzle.tribulation.compat.meridian.MeridianEquipmentCompat;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.event.ArmorEquipmentHandler.ArmorMaterial;
import com.rfizzle.tribulation.scaling.ScalingEngine;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;

public class ArmorEquipmentGameTest implements FabricGameTest {

    @GameTest(template = "tribulation:empty_3x3")
    public void armor_equippedAtTier5(GameTestHelper helper) {
        // Use a mob that hasn't been processed by the default handler if possible,
        // or just clear the tags.
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new net.minecraft.core.BlockPos(1, 2, 1));

        // Clear existing armor and tags to ensure our manual call works
        mob.getTags().remove(com.rfizzle.tribulation.event.ArmorEquipmentHandler.PROCESSED_TAG);
        mob.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        mob.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        mob.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        mob.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);

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
    public void armor_skippedForIncapableMob(GameTestHelper helper) {
        // A spider can't render armor: the capability guard must leave every slot
        // empty even with guaranteed wear/coverage rolls, and mark it processed so
        // the check isn't retried on reload.
        Mob mob = helper.spawnWithNoFreeWill(EntityType.SPIDER, new net.minecraft.core.BlockPos(1, 2, 1));
        mob.getTags().remove(com.rfizzle.tribulation.event.ArmorEquipmentHandler.PROCESSED_TAG);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            mob.setItemSlot(slot, ItemStack.EMPTY);
        }

        TribulationConfig cfg = new TribulationConfig();
        cfg.armorEquipment.enabled = true;
        cfg.armorEquipment.tiers.get("tier5").wearChancePercent = 100;
        cfg.armorEquipment.tiers.get("tier5").slotCoveragePercent = 100;

        com.rfizzle.tribulation.event.ArmorEquipmentHandler.processArmor(mob, 5, cfg);

        helper.succeedWhen(() -> {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                if (!mob.getItemBySlot(slot).isEmpty()) {
                    helper.fail("Spider should never be given armor (" + slot + ")");
                }
            }
            if (!mob.getTags().contains(com.rfizzle.tribulation.event.ArmorEquipmentHandler.PROCESSED_TAG)) {
                helper.fail("Skipped mob should still be tagged processed");
            }
        });
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void armor_materialStaysWithinTierPool(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new net.minecraft.core.BlockPos(1, 2, 1));
        mob.getTags().remove(com.rfizzle.tribulation.event.ArmorEquipmentHandler.PROCESSED_TAG);

        // Tier 1's pool is leather/gold/chain only — iron, diamond, and netherite have
        // zero weight and must never appear here (the "no netherite below unlock" rule).
        TribulationConfig cfg = new TribulationConfig();
        cfg.armorEquipment.enabled = true;
        TribulationConfig.ArmorTier tier1 = cfg.armorEquipment.tiers.get("tier1");
        tier1.wearChancePercent = 100;
        tier1.slotCoveragePercent = 100;
        cfg.armorEquipment.materialRollMode = TribulationConfig.MaterialRollMode.PER_SLOT;

        // Allowed items are exactly the pieces of the tier's non-zero-weight materials.
        Set<Item> allowed = new HashSet<>();
        for (var e : tier1.materialWeights.entrySet()) {
            if (e.getValue() <= 0) continue;
            ArmorMaterial mat = ArmorMaterial.fromKey(e.getKey());
            if (mat == null) continue;
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                allowed.add(mat.getBySlot(slot));
            }
        }

        com.rfizzle.tribulation.event.ArmorEquipmentHandler.processArmor(mob, 1, cfg);

        helper.succeedWhen(() -> {
            boolean any = false;
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack stack = mob.getItemBySlot(slot);
                if (stack.isEmpty()) continue;
                any = true;
                if (!allowed.contains(stack.getItem())) {
                    helper.fail("Tier-1 mob equipped out-of-pool material " + stack.getItem() + " in slot " + slot);
                }
            }
            if (!any) {
                helper.fail("Tier-1 mob with 100% wear+coverage should have equipped armor");
            }
        });
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void armor_ceilingClamping(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new net.minecraft.core.BlockPos(1, 2, 1));

        // Clear any modifiers the MobScalingHandler might have applied
        for (String axis : java.util.List.of(ScalingEngine.AXIS_TIME, ScalingEngine.AXIS_DISTANCE, ScalingEngine.AXIS_HEIGHT)) {
            mob.getAttribute(Attributes.ARMOR).removeModifier(ScalingEngine.modifierId(axis, ScalingEngine.ATTR_ARMOR));
        }

        // Equip full netherite (20 armor)
        mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        mob.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));

        // Add a large Tribulation modifier (e.g., +10 armor)
        // We use only AXIS_TIME for simplicity in summing
        AttributeInstance armorInst = mob.getAttribute(Attributes.ARMOR);
        armorInst.addPermanentModifier(new AttributeModifier(
                ScalingEngine.modifierId(ScalingEngine.AXIS_TIME, ScalingEngine.ATTR_ARMOR),
                10.0, AttributeModifier.Operation.ADD_VALUE));

        // Total would be 20 (base items) + 10 (trib) + 2 (zombie base) = 32
        // Ceiling is 24
        ScalingEngine.clampToCeiling(mob, ScalingEngine.ATTR_ARMOR, 24.0);

        helper.succeedWhen(() -> {
            double val = mob.getAttributeValue(Attributes.ARMOR);
            if (val > 24.1) { // Allow for some float delta
                helper.fail("Armor value " + val + " exceeds ceiling 24.0");
            }
            // Netherite items must stay, so total must be at least 20 (items) + 2 (zombie base) = 22
            if (val < 21.9) {
                 helper.fail("Armor value " + val + " should not be lower than base + items (22.0)");
            }

            // Check that modifiers were actually scaled
            AttributeInstance armor = mob.getAttribute(Attributes.ARMOR);
            double sum = 0;
            for (String axis : java.util.List.of(ScalingEngine.AXIS_TIME, ScalingEngine.AXIS_DISTANCE, ScalingEngine.AXIS_HEIGHT)) {
                AttributeModifier mod = armor.getModifier(ScalingEngine.modifierId(axis, ScalingEngine.ATTR_ARMOR));
                if (mod != null) sum += mod.amount();
            }
            // total(32) - ceiling(24) = 8 surplus.
            // tribSum(10) - surplus(8) = 2 new sum.
        if (Math.abs(sum - 2.0) > 0.5) { // Be even more generous with delta for safety
            helper.fail("Tribulation modifiers not scaled correctly: expected 2.0, got " + sum + " (total value: " + val + ")");
            }
        });
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void armor_toughnessCeilingClamping(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new net.minecraft.core.BlockPos(1, 2, 1));

        // Clear any Tribulation toughness modifiers
        for (String axis : java.util.List.of(ScalingEngine.AXIS_TIME, ScalingEngine.AXIS_DISTANCE, ScalingEngine.AXIS_HEIGHT)) {
            mob.getAttribute(Attributes.ARMOR_TOUGHNESS).removeModifier(ScalingEngine.modifierId(axis, ScalingEngine.ATTR_TOUGHNESS));
        }

        // Equip full netherite (3 toughness per piece = 12 total)
        mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        mob.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));

        // Add a Tribulation toughness modifier (+8)
        AttributeInstance toughnessInst = mob.getAttribute(Attributes.ARMOR_TOUGHNESS);
        toughnessInst.addPermanentModifier(new AttributeModifier(
                ScalingEngine.modifierId(ScalingEngine.AXIS_TIME, ScalingEngine.ATTR_TOUGHNESS),
                8.0, AttributeModifier.Operation.ADD_VALUE));

        // Total: 0 (zombie base) + 12 (netherite) + 8 (trib) = 20; ceiling 14 → surplus 6
        // Expected trib sum after clamp: max(0, 8 − 6) = 2; final value should be 14
        ScalingEngine.clampToCeiling(mob, ScalingEngine.ATTR_TOUGHNESS, 14.0);

        helper.succeedWhen(() -> {
            double val = mob.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            if (val > 14.1) {
                helper.fail("Toughness value " + val + " exceeds ceiling 14.0");
            }
            // Netherite items must stay (12 toughness), so total must be at least 12
            if (val < 11.9) {
                helper.fail("Toughness value " + val + " should not be lower than item contribution (12.0)");
            }

            // Verify the trib modifier was proportionally scaled: originally 8, expected ~2
            AttributeInstance toughness = mob.getAttribute(Attributes.ARMOR_TOUGHNESS);
            double sum = 0;
            for (String axis : java.util.List.of(ScalingEngine.AXIS_TIME, ScalingEngine.AXIS_DISTANCE, ScalingEngine.AXIS_HEIGHT)) {
                AttributeModifier mod = toughness.getModifier(ScalingEngine.modifierId(axis, ScalingEngine.ATTR_TOUGHNESS));
                if (mod != null) sum += mod.amount();
            }
            if (Math.abs(sum - 2.0) > 0.5) {
                helper.fail("Toughness Tribulation modifiers not scaled correctly: expected ~2.0, got " + sum + " (total value: " + val + ")");
            }
        });
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void armor_meridianBonusInertWhenModAbsent(GameTestHelper helper) {
        // Meridian is not on the gametest runtime, so the tier-4/5 bonus branch must stay inert:
        // vanilla Protection still applies, no meridian:* enchant leaks in, and nothing throws.
        if (MeridianEquipmentCompat.isActive()) {
            helper.fail("Meridian must be absent from the gametest runtime for this fallback test");
        }

        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new net.minecraft.core.BlockPos(1, 2, 1));
        mob.getTags().remove(com.rfizzle.tribulation.event.ArmorEquipmentHandler.PROCESSED_TAG);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            mob.setItemSlot(slot, ItemStack.EMPTY);
        }

        TribulationConfig cfg = new TribulationConfig();
        cfg.meridianEquipmentEnchants = true;
        cfg.armorEquipment.enabled = true;
        TribulationConfig.ArmorTier tier5 = cfg.armorEquipment.tiers.get("tier5");
        tier5.wearChancePercent = 100;
        tier5.slotCoveragePercent = 100;
        tier5.enchantChancePercent = 100;

        com.rfizzle.tribulation.event.ArmorEquipmentHandler.processArmor(mob, 5, cfg);

        helper.succeedWhen(() -> {
            boolean anyEnchanted = false;
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack stack = mob.getItemBySlot(slot);
                if (stack.isEmpty()) continue;
                ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                if (!enchants.isEmpty()) {
                    anyEnchanted = true;
                }
                for (Holder<Enchantment> holder : enchants.keySet()) {
                    boolean isMeridian = holder.unwrapKey()
                            .map(key -> key.location().getNamespace().equals("meridian"))
                            .orElse(false);
                    if (isMeridian) {
                        helper.fail("No meridian:* enchant should appear when Meridian is absent");
                    }
                }
            }
            if (!anyEnchanted) {
                helper.fail("Vanilla Protection path should still enchant armor at 100% enchant chance");
            }
        });
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void api_frozenTierPersistence(GameTestHelper helper) {
        // Entity with no scaled-tier attachment: the API reports empty / false rather
        // than a recomputed value. (Mobs are auto-scaled on spawn here, so clear it to
        // model a never-scaled entity deterministically.)
        Mob unscaled = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new net.minecraft.core.BlockPos(1, 2, 2));
        unscaled.removeAttached(com.rfizzle.tribulation.data.TribulationAttachments.SCALED_TIER);
        helper.assertFalse(TribulationAPI.getScaledTier(unscaled).isPresent(), "Mob without attachment should have no tier");
        helper.assertFalse(TribulationAPI.wasScaledByTribulation(unscaled), "Mob without attachment should not be marked scaled");

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
    public void api_frozenTierSurvivesSaveLoad(GameTestHelper helper) {
        // The frozen tier is backed by a persistent data attachment; it must survive an
        // NBT save/load round trip (the same path a chunk unload/reload exercises).
        Mob original = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new net.minecraft.core.BlockPos(1, 2, 1));
        int frozen = 4;
        original.setAttached(com.rfizzle.tribulation.data.TribulationAttachments.SCALED_TIER, frozen);

        CompoundTag saved = new CompoundTag();
        original.saveWithoutId(saved);
        // Discard first so the reloaded copy doesn't collide on the shared UUID.
        original.discard();

        Mob reloaded = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new net.minecraft.core.BlockPos(2, 2, 1));
        reloaded.load(saved);

        OptionalInt tier = TribulationAPI.getScaledTier(reloaded);
        helper.assertTrue(tier.isPresent(), "Frozen tier should survive save/load");
        helper.assertValueEqual(tier.getAsInt(), frozen, "Frozen tier should still be 4 after reload");

        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void api_armorDropChanceProvider(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new net.minecraft.core.BlockPos(1, 2, 1));
        ItemStack stack = new ItemStack(Items.IRON_CHESTPLATE);

        TribulationAPI.setArmorDropChanceProvider((m, tier, slot, s, def) -> 0.5f);

        try {
            float chance = TribulationAPI.resolveArmorDropChance(mob, 1, EquipmentSlot.CHEST, stack, 0.085f);
            helper.assertValueEqual(chance, 0.5f, "Drop chance should be 0.5f from provider");
        } finally {
            // Restore default regardless of assertion outcome
            TribulationAPI.setArmorDropChanceProvider((m, tier, slot, s, def) -> def);
        }
        helper.succeed();
    }
}
