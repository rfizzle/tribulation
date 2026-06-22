// Tier: 3 (Fabric Gametest)
package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.event.RaidScalingHandler;
import com.rfizzle.tribulation.event.WeaponEquipmentHandler;
import com.rfizzle.tribulation.scaling.TierManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * End-to-end coverage of {@link RaidScalingHandler} (patrol-size scaling) and
 * the raider-equipment path that raid/patrol mobs share with every other scaled
 * spawn.
 *
 * <p>The patrol test drives {@link RaidScalingHandler#spawnExtraMembers} with an
 * explicit level so it never has to fight the {@code ENTITY_LOAD}/{@code
 * isPatrolLeader} ordering; it polls (the core defers the add by a tick).
 */
public class RaidScalingGameTest implements FabricGameTest {

    private static final BlockPos CAPTAIN_POS = new BlockPos(1, 2, 1);

    @GameTest(template = "tribulation:empty_3x3")
    public void patrolSizeScalesWithTier(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();

        TribulationConfig cfg = new TribulationConfig();
        cfg.raidScaling.enabled = true;
        cfg.raidScaling.patrolBonusRate = 1; // +1 member per tier

        int playerLevel = cfg.tiers.tier3; // tier 3
        int tier = TierManager.getTier(playerLevel, cfg.tiers);
        int expectedExtra = cfg.raidScaling.extraPatrolMembers(tier);
        helper.assertTrue(expectedExtra > 0, "Test setup must yield at least one extra member");

        Pillager captain = helper.spawnWithNoFreeWill(EntityType.PILLAGER, CAPTAIN_POS);
        captain.setPatrolLeader(true);

        RaidScalingHandler.spawnExtraMembers(captain, world, playerLevel, cfg);

        helper.succeedWhen(() -> {
            List<Pillager> nearby = world.getEntitiesOfClass(
                    Pillager.class, captain.getBoundingBox().inflate(16.0));
            int extras = 0;
            for (Pillager p : nearby) {
                if (p != captain) extras++;
            }
            if (extras != expectedExtra) {
                helper.fail("Expected " + expectedExtra + " extra patrol members, found " + extras);
            }
        });
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void patrolSizeUnchangedWhenDisabled(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();

        TribulationConfig cfg = new TribulationConfig();
        cfg.raidScaling.enabled = false;
        cfg.raidScaling.patrolBonusRate = 1;

        Pillager captain = helper.spawnWithNoFreeWill(EntityType.PILLAGER, CAPTAIN_POS);
        captain.setPatrolLeader(true);

        RaidScalingHandler.spawnExtraMembers(captain, world, cfg.tiers.tier5, cfg);

        // Give the deferred task a window to (not) run, then assert no extras.
        helper.runAfterDelay(3, () -> {
            List<Pillager> nearby = world.getEntitiesOfClass(
                    Pillager.class, captain.getBoundingBox().inflate(16.0));
            if (nearby.size() != 1) {
                helper.fail("Disabled raid scaling must not add patrol members, found "
                        + (nearby.size() - 1) + " extras");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void raiderEquipmentApplied(GameTestHelper helper) {
        // Raiders (pillager/vindicator are AbstractIllager) flow through the same
        // weapon-equipment path as every other scaled mob; validate it end-to-end.
        Mob mob = helper.spawnWithNoFreeWill(EntityType.PILLAGER, CAPTAIN_POS);
        mob.getTags().remove(WeaponEquipmentHandler.PROCESSED_TAG);
        mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);

        TribulationConfig cfg = new TribulationConfig();
        cfg.weaponEquipment.enabled = true;
        TribulationConfig.WeaponTier tier5 = cfg.weaponEquipment.tiers.get("tier5");
        tier5.wearChancePercent = 100;

        Set<Item> allowed = new HashSet<>();
        for (var e : tier5.materialWeights.entrySet()) {
            if (e.getValue() <= 0) continue;
            WeaponEquipmentHandler.WeaponMaterial mat = WeaponEquipmentHandler.WeaponMaterial.fromKey(e.getKey());
            if (mat != null) {
                allowed.add(mat.getSword());
                allowed.add(mat.getAxe());
            }
        }

        WeaponEquipmentHandler.processWeapon(mob, 5, cfg);

        helper.succeedWhen(() -> {
            ItemStack mainHand = mob.getMainHandItem();
            if (mainHand.isEmpty()) {
                helper.fail("Raider should have a tier weapon equipped at tier 5 with 100% wear chance");
            }
            if (!allowed.contains(mainHand.getItem())) {
                helper.fail("Raider equipped out-of-pool weapon " + mainHand.getItem());
            }
        });
    }
}
