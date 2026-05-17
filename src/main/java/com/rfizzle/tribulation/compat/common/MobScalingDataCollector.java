package com.rfizzle.tribulation.compat.common;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.event.MobScalingHandler;
import com.rfizzle.tribulation.event.ZombieVariantHandler;
import com.rfizzle.tribulation.scaling.ScalingEngine;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Collects scaling data from a {@link Mob} into a {@link CompoundTag} for
 * probe-tooltip integrations (Jade, WTHIT). Runs server-side only.
 */
public final class MobScalingDataCollector {

    public static final String KEY_SCALED = "Scaled";
    public static final String KEY_HEALTH_FACTOR = "HealthFactor";
    public static final String KEY_VARIANT = "Variant";
    public static final String KEY_ABILITIES = "Abilities";

    private static final Map<String, String> TAG_ABILITIES = new LinkedHashMap<>();
    private static final Map<String, String> MODIFIER_ABILITIES = new LinkedHashMap<>();

    static {
        TAG_ABILITIES.put("tribulation_web", "Web Placing");
        TAG_ABILITIES.put("tribulation_crop_trample", "Crop Trample");
        TAG_ABILITIES.put("tribulation_hunger2", "Hunger II");

        MODIFIER_ABILITIES.put("ability_zombie_reinforcements", "Reinforcements");
        MODIFIER_ABILITIES.put("ability_zombie_sprint", "Sprint");
        MODIFIER_ABILITIES.put("ability_spider_leap", "Leap Attack");
        MODIFIER_ABILITIES.put("ability_hoglin_kb_resist", "Knockback Resist");
        MODIFIER_ABILITIES.put("ability_zombified_piglin_aggro", "Extended Aggro");
        MODIFIER_ABILITIES.put("ability_wither_skeleton_sprint", "Sprint");
    }

    private MobScalingDataCollector() {}

    public static void collect(Mob mob, CompoundTag data) {
        if (mob == null || data == null) return;

        boolean scaled = mob.getTags().contains(MobScalingHandler.PROCESSED_TAG);
        data.putBoolean(KEY_SCALED, scaled);
        if (!scaled) return;

        data.putDouble(KEY_HEALTH_FACTOR, ScalingEngine.readHealthScalingFactor(mob));
        data.putString(KEY_VARIANT, detectVariant(mob));
        data.putString(KEY_ABILITIES, detectAbilities(mob));
    }

    private static String detectVariant(Mob mob) {
        if (hasModifier(mob, ZombieVariantHandler.BIG_HEALTH_ID)
                || hasModifier(mob, ZombieVariantHandler.BIG_SIZE_ID)) {
            return "big";
        }
        if (hasModifier(mob, ZombieVariantHandler.SPEED_SPEED_ID)
                || hasModifier(mob, ZombieVariantHandler.SPEED_HEALTH_ID)) {
            return "speed";
        }
        return "";
    }

    private static String detectAbilities(Mob mob) {
        List<String> found = new ArrayList<>();

        for (Map.Entry<String, String> entry : TAG_ABILITIES.entrySet()) {
            if (mob.getTags().contains(entry.getKey())) {
                found.add(entry.getValue());
            }
        }

        for (Map.Entry<String, String> entry : MODIFIER_ABILITIES.entrySet()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    Tribulation.MOD_ID, entry.getKey());
            if (hasModifier(mob, id)) {
                found.add(entry.getValue());
            }
        }

        if (found.isEmpty()) return "";
        StringJoiner joiner = new StringJoiner(", ");
        for (String s : found) joiner.add(s);
        return joiner.toString();
    }

    private static boolean hasModifier(Mob mob, ResourceLocation id) {
        for (Map.Entry<String, Holder<Attribute>> entry : ScalingEngine.attributeHolders().entrySet()) {
            AttributeInstance inst = mob.getAttribute(entry.getValue());
            if (inst != null) {
                for (AttributeModifier mod : inst.getModifiers()) {
                    if (id.equals(mod.id())) return true;
                }
            }
        }
        return false;
    }
}
