package com.rfizzle.tribulation.mixin;

import com.rfizzle.tribulation.Tribulation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks {@link Mob#doHurtTarget} to apply tag-gated on-hit abilities set by
 * {@link com.rfizzle.tribulation.ability.AbilityManager}. Covers Spider web
 * placement and crop trampling — these require code at the attack site that
 * the pure-attribute/effect approach in AbilityManager cannot express.
 */
@Mixin(Mob.class)
public abstract class MobAbilityMixin {

    @Inject(method = "doHurtTarget", at = @At("RETURN"))
    private void tribulation$onHurtTarget(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        Mob self = (Mob) (Object) this;

        try {
            if (self.getTags().contains("tribulation_web")) {
                placeWeb(self, target);
            }
            if (self.getTags().contains("tribulation_crop_trample")) {
                trampleCrops(self, target);
            }
        } catch (Exception e) {
            Tribulation.LOGGER.warn("Error in tribulation on-hit abilities for {}", self, e);
        }
    }

    private static void placeWeb(Mob mob, Entity target) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        BlockPos pos = target.blockPosition();
        if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 3);
        }
    }

    private static void trampleCrops(Mob mob, Entity target) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        BlockPos center = target.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 0, 1))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock || state.is(Blocks.FARMLAND)) {
                level.destroyBlock(pos, true);
            }
        }
    }
}
