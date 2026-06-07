package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.api.TribulationAPI;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.config.TribulationConfig.ArmorTier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles the application of tier-driven armor to mobs at spawn.
 * When enabled, it clears vanilla armor and rolls for new equipment based
 * on the mob's Tribulation tier.
 */
public final class ArmorEquipmentHandler {
    public static final String PROCESSED_TAG = "tribulation_armor_processed";

    private ArmorEquipmentHandler() {}

    public enum ArmorMaterial {
        LEATHER(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS),
        GOLD(Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS),
        CHAIN(Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS),
        IRON(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS),
        DIAMOND(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS),
        NETHERITE(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);

        private final Item head;
        private final Item chest;
        private final Item legs;
        private final Item feet;

        ArmorMaterial(Item head, Item chest, Item legs, Item feet) {
            this.head = head;
            this.chest = chest;
            this.legs = legs;
            this.feet = feet;
        }

        public Item getHead() { return head; }
        public Item getChest() { return chest; }
        public Item getLegs() { return legs; }
        public Item getFeet() { return feet; }

        public Item getBySlot(EquipmentSlot slot) {
            return switch (slot) {
                case HEAD -> head;
                case CHEST -> chest;
                case LEGS -> legs;
                case FEET -> feet;
                default -> null;
            };
        }

        public static ArmorMaterial fromKey(String key) {
            try {
                return valueOf(key.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public static void processArmor(Mob mob, int tier, TribulationConfig cfg) {
        if (mob.getTags().contains(PROCESSED_TAG)) return;
        if (!cfg.armorEquipment.enabled) return;

        // Unconditionally clear armor slots for takeover
        mob.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        mob.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        mob.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        mob.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);

        ArmorTier tierCfg = cfg.armorEquipment.tiers.get("tier" + tier);
        if (tierCfg == null || tierCfg.materialWeights.isEmpty()) {
            mob.addTag(PROCESSED_TAG);
            return;
        }

        RandomSource random = mob.getRandom();
        if (random.nextInt(100) >= tierCfg.wearChancePercent) {
            mob.addTag(PROCESSED_TAG);
            return;
        }

        boolean perMob = cfg.armorEquipment.materialRollMode == TribulationConfig.MaterialRollMode.PER_MOB;
        ArmorMaterial sharedMaterial = perMob ? rollMaterial(tierCfg.materialWeights, random) : null;

        applySlot(mob, EquipmentSlot.HEAD, tier, tierCfg, sharedMaterial, random, cfg);
        applySlot(mob, EquipmentSlot.CHEST, tier, tierCfg, sharedMaterial, random, cfg);
        applySlot(mob, EquipmentSlot.LEGS, tier, tierCfg, sharedMaterial, random, cfg);
        applySlot(mob, EquipmentSlot.FEET, tier, tierCfg, sharedMaterial, random, cfg);

        // Armored mobs don't pick up loose loot to avoid "stacking" via player drops
        mob.setCanPickUpLoot(false);
        mob.addTag(PROCESSED_TAG);
    }

    private static void applySlot(Mob mob, EquipmentSlot slot, int tier, ArmorTier tierCfg, ArmorMaterial sharedMaterial, RandomSource random, TribulationConfig cfg) {
        if (random.nextInt(100) >= tierCfg.slotCoveragePercent) return;

        ArmorMaterial material = (sharedMaterial != null) ? sharedMaterial : rollMaterial(tierCfg.materialWeights, random);
        if (material == null) return;

        Item item = material.getBySlot(slot);
        if (item == null) return;

        ItemStack stack = new ItemStack(item);

        // Optional Protection enchant
        if (tierCfg.enchantChancePercent > 0 && tierCfg.maxProtectionLevel > 0) {
            if (random.nextInt(100) < tierCfg.enchantChancePercent) {
                int level = rollProtectionLevel(tierCfg.maxProtectionLevel, random);
                if (level > 0) {
                    mob.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                            .get(Enchantments.PROTECTION)
                            .ifPresent(holder -> stack.enchant(holder, level));
                }
            }
        }

        mob.setItemSlot(slot, stack);
        float dropChance = (float) cfg.armorEquipment.armorDropChance;
        mob.setDropChance(slot, TribulationAPI.resolveArmorDropChance(mob, tier, slot, stack, dropChance));
    }

    /** Pure logic for weighted material selection. */
    public static ArmorMaterial rollMaterial(Map<String, Integer> weights, RandomSource random) {
        if (weights == null || weights.isEmpty()) return null;
        int totalWeight = 0;
        for (int w : weights.values()) {
            totalWeight += Math.max(0, w);
        }
        if (totalWeight <= 0) return null;

        int roll = random.nextInt(totalWeight);
        int cursor = 0;
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            cursor += Math.max(0, entry.getValue());
            if (roll < cursor) {
                return ArmorMaterial.fromKey(entry.getKey());
            }
        }
        return null;
    }

    /** Low-biased protection roll: rand(1, max) + rand(0, max) / 2 equivalent. */
    public static int rollProtectionLevel(int max, RandomSource random) {
        if (max <= 0) return 0;
        if (max == 1) return 1;
        // Low-bias: roll twice and take the minimum for a slight bias towards lower levels
        return Math.min(random.nextInt(max) + 1, random.nextInt(max) + 1);
    }
}
