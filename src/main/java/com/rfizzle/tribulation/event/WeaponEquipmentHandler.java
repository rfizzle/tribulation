package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.api.TribulationAPI;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.config.TribulationConfig.WeaponTier;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.Map;

/**
 * Handles the application of tier-driven weapons to mobs at spawn.
 * Mirror of {@link ArmorEquipmentHandler} for offensive gear.
 */
public final class WeaponEquipmentHandler {
    public static final String PROCESSED_TAG = "tribulation_weapon_processed";

    private WeaponEquipmentHandler() {}

    public enum WeaponMaterial {
        WOOD(Items.WOODEN_SWORD, Items.WOODEN_AXE),
        STONE(Items.STONE_SWORD, Items.STONE_AXE),
        IRON(Items.IRON_SWORD, Items.IRON_AXE),
        DIAMOND(Items.DIAMOND_SWORD, Items.DIAMOND_AXE),
        NETHERITE(Items.NETHERITE_SWORD, Items.NETHERITE_AXE);

        private final Item sword;
        private final Item axe;

        WeaponMaterial(Item sword, Item axe) {
            this.sword = sword;
            this.axe = axe;
        }

        public Item getSword() { return sword; }
        public Item getAxe() { return axe; }

        public static WeaponMaterial fromKey(String key) {
            try {
                return valueOf(key.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /**
     * Whether this mob actually wields and renders a main-hand weapon in vanilla.
     * The zombie, skeleton, and piglin families hold and swing melee/ranged gear,
     * and illagers (pillager/vindicator) render and use a held weapon via their
     * {@code ItemInHandLayer}. Everything else (creeper, spider, guardian, …) can't
     * use a weapon, so a rolled one would be invisible dead weight. Gating here
     * guards against config toggles or modded callers handing such a mob to
     * {@link #processWeapon}.
     */
    public static boolean supportsWeapons(Mob mob) {
        return mob instanceof Zombie
                || mob instanceof AbstractSkeleton
                || mob instanceof AbstractPiglin
                || mob instanceof AbstractIllager;
    }

    public static void processWeapon(Mob mob, int tier, TribulationConfig cfg) {
        TribulationConfig.WeaponEquipment we = cfg.weaponEquipment;
        if (we == null || !we.enabled || mob.getTags().contains(PROCESSED_TAG)) return;
        // Skip mobs that can't wield a weapon. Tag them so the (unchanging)
        // capability check isn't repeated on every chunk reload.
        if (!supportsWeapons(mob)) {
            mob.addTag(PROCESSED_TAG);
            return;
        }
        if (mob.isBaby()) {
            mob.addTag(PROCESSED_TAG);
            return;
        }

        try {
            equipWeapon(mob, tier, we, cfg);
        } catch (Exception e) {
            Tribulation.LOGGER.warn("Failed to apply tier weapon to {}", mob, e);
        }
        mob.addTag(PROCESSED_TAG);
    }

    /**
     * Core weapon application logic with the following precedence:
     * 1. If the roll for {@code wearChancePercent} fails, the mob's existing loadout
     *    is left untouched — a bare-handed mob stays bare, and a vanilla- or
     *    ability-armed mob keeps its weapon.
     * 2. If holding a standard melee item (likely from {@link com.rfizzle.tribulation.ability.AbilityManager}),
     *    it is upgraded to the rolled material.
     * 3. If holding a standard ranged item, it is kept and moves straight to the
     *    enchantment phase.
     * 4. If bare-handed, a sword of the rolled material is given.
     */
    private static void equipWeapon(Mob mob, int tier, TribulationConfig.WeaponEquipment we, TribulationConfig cfg) {
        WeaponTier tierCfg = we.tiers.get("tier" + tier);
        if (tierCfg == null || tierCfg.materialWeights == null || tierCfg.materialWeights.isEmpty()) return;

        RandomSource random = mob.getRandom();
        ItemStack current = mob.getMainHandItem();

        // Roll for wear chance. On a failed roll we leave the mob's existing loadout
        // untouched. Unlike armor — where the handler clears all four slots first to
        // take over vanilla's roll — stripping the main hand here would disarm every
        // vanilla-armed mob (skeleton's bow, pillager's crossbow, vindicator's axe,
        // drowned's trident, …) and break the attack AI that depends on that weapon.
        if (random.nextInt(100) >= tierCfg.wearChancePercent) {
            return;
        }

        WeaponMaterial material = rollMaterial(tierCfg.materialWeights, random);
        if (material == null) return;

        ItemStack stack;
        if (current.isEmpty() || isStandardMelee(current)) {
            // Replace with rolled material
            Item newItem = (current.getItem() instanceof net.minecraft.world.item.AxeItem) ? material.getAxe() : material.getSword();
            stack = new ItemStack(newItem);
        } else if (isStandardRanged(current)) {
            // Keep ranged item, just prepare for enchantment
            stack = current.copy();
        } else {
            // Unrecognized item, don't touch it to avoid breaking specialized mobs
            return;
        }

        // Enchantments
        if (tierCfg.enchantChancePercent > 0 && tierCfg.maxEnchantmentLevel > 0) {
            if (random.nextInt(100) < tierCfg.enchantChancePercent) {
                applyEnchantments(mob, stack, tier, tierCfg, random);
            }
        }

        mob.setItemSlot(EquipmentSlot.MAINHAND, stack);
        float dropChance = (float) we.weaponDropChance;
        mob.setDropChance(EquipmentSlot.MAINHAND, TribulationAPI.resolveWeaponDropChance(mob, tier, stack, dropChance));
    }

    private static boolean isStandardMelee(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof SwordItem || item instanceof DiggerItem || item instanceof net.minecraft.world.item.MaceItem;
    }

    private static boolean isStandardRanged(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.BOW || item == Items.CROSSBOW || item == Items.TRIDENT;
    }

    private static void applyEnchantments(Mob mob, ItemStack stack, int tier, WeaponTier tierCfg, RandomSource random) {
        var lookup = mob.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int max = tierCfg.maxEnchantmentLevel;

        if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof net.minecraft.world.item.AxeItem) {
            // Sharpness (base)
            int level = rollLevel(max, random);
            lookup.get(Enchantments.SHARPNESS).ifPresent(h -> stack.enchant(h, level));

            // Knockback (Tier 4+, rare)
            if (tier >= 4 && random.nextInt(10) == 0) {
                lookup.get(Enchantments.KNOCKBACK).ifPresent(h -> stack.enchant(h, Math.min(2, level)));
            }
            // Fire Aspect (Tier 5+, rare)
            if (tier >= 5 && random.nextInt(10) == 0) {
                lookup.get(Enchantments.FIRE_ASPECT).ifPresent(h -> stack.enchant(h, Math.min(2, level)));
            }
        } else if (stack.is(Items.BOW)) {
            // Power (base)
            int level = rollLevel(max, random);
            lookup.get(Enchantments.POWER).ifPresent(h -> stack.enchant(h, level));

            // Punch (Tier 4+, rare)
            if (tier >= 4 && random.nextInt(10) == 0) {
                lookup.get(Enchantments.PUNCH).ifPresent(h -> stack.enchant(h, Math.min(2, level)));
            }
            // Flame (Tier 5+, rare)
            if (tier >= 5 && random.nextInt(10) == 0) {
                lookup.get(Enchantments.FLAME).ifPresent(h -> stack.enchant(h, 1));
            }
        } else if (stack.is(Items.CROSSBOW)) {
            // Quick Charge (base)
            int level = rollLevel(max, random);
            lookup.get(Enchantments.QUICK_CHARGE).ifPresent(h -> stack.enchant(h, Math.min(3, level)));

            // Piercing or Multishot
            if (random.nextBoolean()) {
                lookup.get(Enchantments.PIERCING).ifPresent(h -> stack.enchant(h, Math.min(4, level)));
            } else {
                lookup.get(Enchantments.MULTISHOT).ifPresent(h -> stack.enchant(h, 1));
            }
        } else if (stack.is(Items.TRIDENT)) {
            // Impaling (base)
            int level = rollLevel(max, random);
            lookup.get(Enchantments.IMPALING).ifPresent(h -> stack.enchant(h, level));
        }
    }

    private static int rollLevel(int max, RandomSource random) {
        if (max <= 0) return 0;
        if (max == 1) return 1;
        return Math.min(random.nextInt(max) + 1, random.nextInt(max) + 1);
    }

    public static WeaponMaterial rollMaterial(Map<String, Integer> weights, RandomSource random) {
        int total = 0;
        for (int w : weights.values()) total += Math.max(0, w);
        if (total <= 0) return null;

        int roll = random.nextInt(total);
        int cursor = 0;
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            int w = Math.max(0, entry.getValue());
            if (w <= 0) continue;
            cursor += w;
            if (roll < cursor) return WeaponMaterial.fromKey(entry.getKey());
        }
        return null;
    }
}
