package com.rfizzle.tribulation.mixin;

import com.rfizzle.tribulation.ability.AbilityManager;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Two tier abilities for the {@link Witch}, both driven by vanilla mechanics:
 *
 * <ul>
 *   <li><b>Lingering potions (tier 3+)</b> — redirects the single
 *       {@code Items.SPLASH_POTION} read in {@code performRangedAttack} to
 *       {@code Items.LINGERING_POTION}, so the thrown potion leaves an
 *       area-effect cloud instead of a one-shot splash.</li>
 *   <li><b>Aggressive healing (tier 5+)</b> — raises the per-tick probability
 *       that {@code aiStep} elects to drink a healing potion (vanilla {@code 0.05}),
 *       so a wounded witch heals far sooner and more often.</li>
 * </ul>
 *
 * Both are gated per-mob by scoreboard tags, so untagged witches behave exactly
 * as vanilla.
 */
@Mixin(Witch.class)
public abstract class WitchAbilityMixin {

    @Redirect(
            method = "performRangedAttack",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/item/Items;SPLASH_POTION:Lnet/minecraft/world/item/Item;",
                    opcode = Opcodes.GETSTATIC
            )
    )
    private Item tribulation$lingeringPotions() {
        Mob self = (Mob) (Object) this;
        return self.getTags().contains(AbilityManager.TAG_LINGERING_POTIONS)
                ? Items.LINGERING_POTION
                : Items.SPLASH_POTION;
    }

    @ModifyConstant(method = "aiStep", constant = @Constant(floatValue = 0.05f))
    private float tribulation$aggressiveHealing(float original) {
        Mob self = (Mob) (Object) this;
        return self.getTags().contains(AbilityManager.TAG_AGGRESSIVE_HEALING) ? 0.25f : original;
    }
}
