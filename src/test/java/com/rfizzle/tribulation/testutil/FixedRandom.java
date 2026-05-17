package com.rfizzle.tribulation.testutil;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/**
 * Deterministic RandomSource that returns a fixed double from nextDouble().
 * Only nextDouble() and nextFloat() are implemented; all other methods throw.
 */
public final class FixedRandom implements RandomSource {
    private final double value;

    public FixedRandom(double value) {
        this.value = value;
    }

    @Override public RandomSource fork() { return this; }
    @Override public PositionalRandomFactory forkPositional() { throw new UnsupportedOperationException(); }
    @Override public void setSeed(long seed) {}
    @Override public int nextInt() { throw new UnsupportedOperationException(); }
    @Override public int nextInt(int bound) { throw new UnsupportedOperationException(); }
    @Override public long nextLong() { throw new UnsupportedOperationException(); }
    @Override public boolean nextBoolean() { throw new UnsupportedOperationException(); }
    @Override public float nextFloat() { return (float) value; }
    @Override public double nextDouble() { return value; }
    @Override public double nextGaussian() { throw new UnsupportedOperationException(); }
}
