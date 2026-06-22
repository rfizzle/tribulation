package com.rfizzle.tribulation.data;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * Datagen entrypoint. Registered under {@code fabric-datagen} in
 * {@code fabric.mod.json}; runs in its own Gradle task ({@code runDatagen})
 * and writes to {@code src/main/generated/}.
 */
public class TribulationDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        FabricDataGenerator.Pack pack = generator.createPack();
        pack.addProvider(TribulationAdvancementProvider::new);
    }
}
