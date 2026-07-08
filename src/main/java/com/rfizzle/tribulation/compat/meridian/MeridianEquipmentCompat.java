package com.rfizzle.tribulation.compat.meridian;

import com.rfizzle.tribulation.Tribulation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Meridian soft-dependency integration for scaled-mob equipment: when Meridian is installed,
 * a tier-4/5 mob's weapon or armor draws a small bonus of combat enchants from Meridian's
 * curated {@code meridian:mob_equipment} enchantment tag, on top of the vanilla enchants the
 * equipment handlers already apply. Meridian owns which enchants are mob-appropriate (the tag)
 * and their balance ({@code EnchantmentInfo}); Tribulation owns which mobs get scaled gear.
 *
 * <p>The bonus is filtered to enchants that actually apply to the item ({@code canEnchant}),
 * that Meridian has not disabled, and that are compatible with what the item already carries,
 * then clamped to the smaller of Tribulation's per-tier cap and Meridian's configured
 * {@code getMaxLevel()} — so far-scaled gear is flavored, never maxed.
 *
 * <p>This class must only be class-loaded behind an {@code isModLoaded("meridian")} guard
 * (Concord API Standard v1): its own body names no Meridian type, and every reference to
 * {@code com.rfizzle.meridian.api} is confined to the nested {@link Api} holder, which the JVM
 * resolves only once {@link #augment} runs. The whole augmentation is wrapped in
 * {@code catch (Throwable)} — an older Meridian jar missing a method surfaces as a
 * {@code LinkageError}, which must degrade to the vanilla-enchanted item, never break spawning.
 */
public final class MeridianEquipmentCompat {

    /** Meridian's curated pool of mob-appropriate enchants. Absent until Meridian's data loads. */
    private static final TagKey<Enchantment> MOB_EQUIPMENT = TagKey.create(
            Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath("meridian", "mob_equipment"));

    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean(false);

    private MeridianEquipmentCompat() {
    }

    /** Whether Meridian is present, so the equipment handlers should attempt the bonus draw. */
    public static boolean isActive() {
        return FabricLoader.getInstance().isModLoaded("meridian");
    }

    /**
     * Applies the Meridian enchant bonus to {@code stack} in place. A no-op — leaving the item
     * with exactly its vanilla enchants — when the tag is absent or empty, nothing in it applies
     * to the item, or any Meridian call fails.
     *
     * <p>Call only after {@link #isActive()} and the mob's tier ({@code >= 4}) and config toggle
     * have been checked; this method assumes Meridian is present.
     *
     * @param mob          the mob being equipped (source of the enchantment registry)
     * @param stack        the weapon or armor piece, already carrying its vanilla enchants
     * @param tier         the mob's Tribulation tier (4 or 5)
     * @param tierMaxLevel Tribulation's per-tier enchant cap for this slot
     * @param random       the mob's random source
     */
    public static void augment(Mob mob, ItemStack stack, int tier, int tierMaxLevel, RandomSource random) {
        if (stack.isEmpty() || tierMaxLevel <= 0) {
            return;
        }
        try {
            HolderSet.Named<Enchantment> pool = mob.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .get(MOB_EQUIPMENT)
                    .orElse(null);
            if (pool == null || pool.size() == 0) {
                return;
            }

            // Collect the enchants from the tag that apply to this item and that Meridian has not
            // disabled, each with its effective cap. Compatibility with already-present enchants
            // is checked at apply time so inter-bonus conflicts are handled too.
            List<Holder<Enchantment>> candidates = new ArrayList<>();
            List<Integer> effectiveMax = new ArrayList<>();
            for (Holder<Enchantment> holder : pool) {
                if (!holder.value().canEnchant(stack)) {
                    continue;
                }
                int meridianMax = Api.enabledMaxLevel(holder);
                int effective = MeridianEnchantSelection.effectiveMaxLevel(tierMaxLevel, meridianMax);
                if (effective >= 1) {
                    candidates.add(holder);
                    effectiveMax.add(effective);
                }
            }
            if (candidates.isEmpty()) {
                return;
            }

            int[] caps = new int[effectiveMax.size()];
            for (int i = 0; i < caps.length; i++) {
                caps[i] = effectiveMax.get(i);
            }
            int[] levels = MeridianEnchantSelection.rollLevels(
                    caps, MeridianEnchantSelection.bonusEnchantCount(tier), random);

            for (int i = 0; i < levels.length; i++) {
                if (levels[i] <= 0) {
                    continue;
                }
                Holder<Enchantment> holder = candidates.get(i);
                if (compatibleWithPresent(holder, stack)) {
                    stack.enchant(holder, levels[i]);
                }
            }
        } catch (Throwable e) {
            // Throwable, not Exception: an older Meridian jar missing an API method surfaces as a
            // LinkageError, which must not escape into mob spawning. The item keeps its vanilla
            // enchants; log once so the hot path never spams.
            if (FAILURE_LOGGED.compareAndSet(false, true)) {
                Tribulation.LOGGER.warn("Meridian enchant bonus failed; keeping vanilla enchants"
                        + " for this and any further failing spawns", e);
            }
        }
    }

    /**
     * Whether {@code candidate} can coexist with every enchant already on {@code stack} — no
     * exclusive-set conflict (e.g. a Sharpness-class vanilla enchant and a Meridian damage
     * enchant) and not already present.
     */
    private static boolean compatibleWithPresent(Holder<Enchantment> candidate, ItemStack stack) {
        ItemEnchantments present = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> existing : present.keySet()) {
            if (existing.equals(candidate) || !Enchantment.areCompatible(candidate, existing)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The only holder of {@code com.rfizzle.meridian.api} references. Nested so the JVM resolves
     * Meridian's classes only when {@link #augment} first calls in — after the caller's
     * {@code isModLoaded} guard has passed.
     */
    private static final class Api {
        private Api() {
        }

        /**
         * Meridian's configured effective max level for {@code holder}, or {@code 0} when Meridian
         * has the enchant disabled by config.
         */
        static int enabledMaxLevel(Holder<Enchantment> holder) {
            com.rfizzle.meridian.api.EnchantmentInfo info =
                    com.rfizzle.meridian.api.MeridianAPI.getEnchantmentInfo(holder);
            return info.enabled() ? info.getMaxLevel() : 0;
        }
    }
}
