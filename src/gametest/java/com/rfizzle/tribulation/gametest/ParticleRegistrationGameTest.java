// Tier: 3 (Fabric Gametest)
package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.particle.TribulationParticles;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Smoke test that the three threat-telegraphing particle types resolve in
 * {@link BuiltInRegistries#PARTICLE_TYPE} after mod init — guarding the
 * {@code TribulationParticles.register()} wiring. Rendering itself (the client
 * sprites and the emitter) has no headless-server context and is verified
 * manually with {@code runClient}.
 */
public class ParticleRegistrationGameTest implements FabricGameTest {

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void threatParticleTypesAreRegistered(GameTestHelper helper) {
        assertRegistered(helper, "threat_tier", TribulationParticles.THREAT_TIER);
        assertRegistered(helper, "threat_big", TribulationParticles.THREAT_BIG);
        assertRegistered(helper, "threat_speed", TribulationParticles.THREAT_SPEED);
        helper.succeed();
    }

    private static void assertRegistered(GameTestHelper helper, String path, ParticleType<?> type) {
        ResourceLocation expected = Tribulation.id(path);
        ResourceLocation actual = BuiltInRegistries.PARTICLE_TYPE.getKey(type);
        helper.assertTrue(expected.equals(actual),
                "particle type " + expected + " must be registered, got key " + actual);
    }
}
