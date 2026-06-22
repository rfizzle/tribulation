package com.rfizzle.tribulation.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;

import java.util.Optional;

/**
 * Fires when a player crosses into a difficulty tier. Each tier advancement
 * carries a {@code minTier}; a crossing into tier N grants every advancement
 * with {@code minTier <= N}, so a single multi-tier jump still completes the
 * chain rather than leaving a gap.
 */
public class TierReachedCriterion extends SimpleCriterionTrigger<TierReachedCriterion.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, int reachedTier) {
        this.trigger(player, instance -> instance.matches(reachedTier));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, int minTier)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("min_tier").forGetter(TriggerInstance::minTier)
        ).apply(instance, TriggerInstance::new));

        public static TriggerInstance forTier(int minTier) {
            return new TriggerInstance(Optional.empty(), minTier);
        }

        public boolean matches(int reachedTier) {
            return reachedTier >= minTier;
        }
    }
}
