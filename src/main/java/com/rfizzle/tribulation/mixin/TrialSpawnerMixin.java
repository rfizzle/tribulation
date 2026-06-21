package com.rfizzle.tribulation.mixin;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.ability.AbilityManager;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.data.TribulationAttachments;
import com.rfizzle.tribulation.event.ArmorEquipmentHandler;
import com.rfizzle.tribulation.event.MobScalingHandler;
import com.rfizzle.tribulation.event.WeaponEquipmentHandler;
import com.rfizzle.tribulation.event.ZombieVariantHandler;
import com.rfizzle.tribulation.scaling.ScalingEngine;
import com.rfizzle.tribulation.scaling.TierManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Mixin(TrialSpawner.class)
public abstract class TrialSpawnerMixin implements TrialSpawnerAccessor {

    @Inject(method = "spawnMob", at = @At("RETURN"))
    private void tribulation$applyScalingToTrialMob(ServerLevel world, BlockPos pos, CallbackInfoReturnable<Optional<UUID>> cir) {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.trialSpawner.enabled) return;

        Optional<UUID> spawnedUuid = cir.getReturnValue();
        if (spawnedUuid.isEmpty()) return;

        Entity entity = world.getEntity(spawnedUuid.get());
        if (!(entity instanceof Mob mob)) return;

        // Skip if already processed (though it shouldn't be yet)
        if (mob.getTags().contains(MobScalingHandler.PROCESSED_TAG)) return;

        TrialSpawnerData data = this.getData();
        Set<UUID> detectedPlayers = ((TrialSpawnerDataAccessor) data).getDetectedPlayers();

        if (detectedPlayers.isEmpty()) return;

        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(world.getServer());
        List<Integer> levels = new ArrayList<>();
        for (UUID uuid : detectedPlayers) {
            levels.add(state.getLevel(uuid));
        }

        int effectiveLevel = ScalingEngine.foldLevels(cfg.general.scalingMode, levels);
        tribulation$processTrialMob(mob, world, effectiveLevel, cfg);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/trialspawner/TrialSpawnerData;isReadyToSpawn(Lnet/minecraft/server/level/ServerLevel;D)Z"), remap = false)
    private void tribulation$checkOminousUpgrade(ServerLevel world, BlockPos pos, CallbackInfo ci) {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.trialSpawner.enabled || !cfg.trialSpawner.ominousUpgrade.enabled) return;

        TrialSpawner self = (TrialSpawner) (Object) this;
        if (self.isOminous()) return;

        TrialSpawnerData data = this.getData();
        Set<UUID> detectedPlayers = ((TrialSpawnerDataAccessor) data).getDetectedPlayers();
        if (detectedPlayers.isEmpty()) return;

        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(world.getServer());
        List<Integer> levels = new ArrayList<>();
        for (UUID uuid : detectedPlayers) {
            levels.add(state.getLevel(uuid));
        }

        int effectiveLevel = ScalingEngine.foldLevels(cfg.general.scalingMode, levels);
        int tier = TierManager.getTier(effectiveLevel, cfg.tiers);

        if (tier >= cfg.trialSpawner.ominousUpgrade.minimumTier) {
            if (world.getRandom().nextFloat() < cfg.trialSpawner.ominousUpgrade.chance) {
                self.applyOminous(world, pos);
            }
        }
    }

    @Unique
    private void tribulation$processTrialMob(Mob mob, ServerLevel world, int playerLevel, TribulationConfig cfg) {
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());

        if (MobScalingHandler.isExcluded(typeId, cfg.general.excludedEntities)) {
            return;
        }

        TribulationConfig.MobScaling scaling = cfg.resolveScalingForEntity(typeId, mob);
        if (scaling == null) {
            return;
        }

        int tier = TierManager.getTier(playerLevel, cfg.tiers);
        mob.setAttached(TribulationAttachments.SCALED_TIER, tier);

        ScalingEngine.applyModifiers(mob, world, playerLevel, cfg, scaling);

        String toggleKey = MobScalingHandler.resolveToggleKey(typeId);
        if (toggleKey != null && cfg.isMobEnabled(toggleKey)) {
            ArmorEquipmentHandler.processArmor(mob, tier, cfg);
            AbilityManager.applyAbilities(mob, tier, toggleKey, cfg);
            WeaponEquipmentHandler.processWeapon(mob, tier, cfg);
            ZombieVariantHandler.apply(mob, toggleKey, cfg.specialZombies, world.getRandom());
        }

        if (cfg.armorEquipment.enabled) {
            ScalingEngine.clampToCeiling(mob, ScalingEngine.ATTR_ARMOR, cfg.armorEquipment.armorCeiling);
            ScalingEngine.clampToCeiling(mob, ScalingEngine.ATTR_TOUGHNESS, cfg.armorEquipment.toughnessCeiling);
        }

        if (cfg.weaponEquipment.enabled) {
            ScalingEngine.clampToCeiling(mob, ScalingEngine.ATTR_DAMAGE, cfg.weaponEquipment.damageCeiling);
        }

        mob.setHealth(mob.getMaxHealth());
        mob.addTag(MobScalingHandler.PROCESSED_TAG);
    }
}
