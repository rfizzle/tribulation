---
name: fabric-testing
description: Write or modify tests for the Tribulation Fabric mod. Encodes the three-tier decision tree (pure JUnit / fabric-loader-junit / Fabric Gametest). TRIGGER when a *Test.java file is being created or edited, or when the user asks about testing a Fabric mod without a full server.
---

The user is writing or modifying tests in this Fabric mod. Apply this guidance whenever test code is being touched.

## Test infrastructure (already configured)

This project uses:
- **fabric-loader-junit** for Tier 2 tests (already in `build.gradle` dependencies)
- **Fabric Gametest** source set (already wired in `build.gradle`)
- The `testRuntimeClasspath` exclusion for `fabric-api` is already in place
- No `forkEvery` — tests share a JVM safely

## Decision tree — pick one tier per test

Ask these in order and stop at the first "yes":

1. **Does the test reference any `net.minecraft.*` or `net.fabricmc.*` class?**
   No → **Tier 1: Pure JUnit**. Write a normal `@Test`, no framework, no bootstrap. Example: pure math tests, config validation, helper utilities.

2. **Does the test need a real `ServerLevel`, tick loop, entity behavior, block placement, or redstone?**
   Yes → **Tier 3: Gametest**. Use `@GameTest` with a `GameTestHelper`. Runs on `./gradlew runGametest`.

3. **Everything else** (vanilla registries, enchantments, payload codecs, mixin accessors, AW-widened members) → **Tier 2: `fabric-loader-junit`** + explicit `@BeforeAll Bootstrap.bootStrap()`. Knot applies mixins/AWs; bootstrap populates the vanilla registries.

If the test needs to see the **mod's own registered content** (e.g. custom items in `BuiltInRegistries.ITEM`), there is no clean Tier 2 path — fabric-loader-junit does not run `onInitialize`, `Bootstrap.bootStrap()` freezes the registries, and registering post-freeze is prohibited. Push that test to Tier 3.

Write the tier into a `// Tier: N` comment at the top of every new test file.

## Tier 1: pure JUnit

Example of what qualifies:

```java
class ScalingEngineTest {
    @Test
    void timeFactor_cappedBeforeMaxLevel() {
        assertEquals(2.5, ScalingEngine.computeTimeFactor(500, 0.01, 2.5), 1e-9);
    }
}
```

No Minecraft imports → Tier 1 → done.

## Tier 2: fabric-loader-junit

> **Important:** fabric-loader-junit's `FabricLoaderLauncherSessionListener` only initializes Knot's classloader. It does **not** call `Bootstrap.bootStrap()`, and it does **not** invoke the `main` / `ModInitializer` entrypoint. Tests that touch `BuiltInRegistries` still need `@BeforeAll Bootstrap.bootStrap()`.

### Test template

```java
// Tier: 2 (fabric-loader-junit)
package com.rfizzle.tribulation.<area>;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExampleTest {
    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void vanillaRegistriesAreAvailable() {
        assertNotNull(Items.DIAMOND_SWORD);
        assertTrue(BuiltInRegistries.ITEM.containsKey(BuiltInRegistries.ITEM.getKey(Items.DIAMOND_SWORD)));
    }
}
```

### What Tier 2 gives you

- Knot classloader applies your mixins and AWs
- `Bootstrap.bootStrap()` populates `Attributes`, `Items`, `BuiltInRegistries`, etc.
- `Zombie.createAttributes().build()` and other entity supplier calls work
- You can add `AttributeModifier`s to a real `AttributeMap` and observe the resulting `getValue()`
- Does **not** run the mod's `onInitialize` — mod items/blocks are not registered

### Tier 2 sweet spot

Bridge tests that prove pure-math results actually land correctly when applied to real vanilla objects. For example: verifying that a computed attribute factor produces the expected `getMaxHealth()` when applied to a real vanilla `AttributeMap`.

## Tier 3: Fabric Gametest

Use when a test must drive a real `Level` — entity behavior, block placement, player interaction flows, or any case where the mod's own registered content has to be present.

### Test template

```java
// Tier: 3 (Fabric Gametest)
package com.rfizzle.tribulation.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

public class SomeGameTest implements FabricGameTest {
    @GameTest(template = "tribulation:empty_3x3")
    public void placeAndBreakBlock(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, Blocks.ANVIL);
        helper.assertBlockPresent(Blocks.ANVIL, pos);
        helper.succeed();
    }
}
```

Register gametest classes via `fabric-gametest` entrypoint in `src/main/resources/fabric.mod.json`.

### Runtime patterns

**1. Mock player positioning.** `helper.makeMockServerPlayerInLevel()` places the player near the level's (0,0,0), not inside the test region. Teleport into the test region:

```java
ServerPlayer player = helper.makeMockServerPlayerInLevel();
BlockPos playerAbs = helper.absolutePos(new BlockPos(0, 2, 1));
player.teleportTo(playerAbs.getX() + 0.5, playerAbs.getY(), playerAbs.getZ() + 0.5);
```

**2. Deterministic assertions.** The gametest world's position is randomized, so disable noisy paths when asserting a single axis:

```java
boolean savedDist = cfg.distanceScaling.enabled;
cfg.distanceScaling.enabled = false;
try {
    zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
} finally {
    cfg.distanceScaling.enabled = savedDist;
}
```

**3. `helper.assertValueEqual` is exact equality.** No tolerance parameter. For fractional values use `helper.assertTrue(Math.abs(actual - expected) < 1e-4, "...")`.

**4. Synchronous vs deferred assertions.** `helper.succeedWhen(() -> { ... })` polls every tick. Use for state that needs a tick (AI, projectiles, block entities). Direct assertions work for state ready before `spawn*` returns.

### Template location

Structure templates: `src/main/resources/data/tribulation/gametest/structure/<name>.snbt`

Templates **must** be in `src/main/resources`, not `src/gametest/resources`.

### Running

```bash
./gradlew runGametest
```

### Do not use gametest for

- Pure math or formula checks — Tier 1.
- Registry lookups, item creation, component wiring — Tier 2 is faster.
- Handler unit tests ("given this ItemStack, when I call handler.handle(...), then X") — Tier 2.

## Guardrails

- **Do not** skip `Bootstrap.bootStrap()` in a Tier 2 test that touches `BuiltInRegistries`. Knot does not call it for you.
- **Do not** try to register the mod's own items in a Tier 2 `@BeforeAll`. Route those tests to Tier 3.
- **Do not** use reflection on `MappedRegistry` in new code.
- **Do not** assume a test needs Tier 2 just because it imports a Minecraft class. A pure POJO (e.g. `BlockPos`, `RandomSource` as an interface) does not require Fabric boot — try Tier 1 first.
- **Do not** widen production method access to make a gametest reach it. Use public surface area or test observable behavior.

## When asked to add a new test

1. Run the decision tree. Commit to a tier before writing any code.
2. Put the `// Tier: N` comment at the top.
3. Use the matching template.
4. Run the single test with `./gradlew test --tests '<fully.qualified.TestName>'` before claiming it passes.
