package com.rfizzle.tribulation.testutil;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/**
 * RandomSource that fails any call — proves short-circuit paths don't roll.
 */
public final class ThrowingRandom implements RandomSource {
    @Override public RandomSource fork() { return this; }
    @Override public PositionalRandomFactory forkPositional() { throw new UnsupportedOperationException(); }
    @Override public void setSeed(long seed) {}
    @Override public int nextInt() { throw new AssertionError("unexpected nextInt"); }
    @Override public int nextInt(int bound) { throw new AssertionError("unexpected nextInt(bound)"); }
    @Override public long nextLong() { throw new AssertionError("unexpected nextLong"); }
    @Override public boolean nextBoolean() { throw new AssertionError("unexpected nextBoolean"); }
    @Override public float nextFloat() { throw new AssertionError("unexpected nextFloat"); }
    @Override public double nextDouble() { throw new AssertionError("unexpected nextDouble"); }
    @Override public double nextGaussian() { throw new AssertionError("unexpected nextGaussian"); }
}
