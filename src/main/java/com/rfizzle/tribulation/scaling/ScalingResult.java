package com.rfizzle.tribulation.scaling;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable summary of the scaling computed for one mob at one location.
 * Holds the per-axis inputs and the per-attribute factor breakdown so
 * callers (e.g. {@code /tribulation inspect}) can display what each
 * axis contributed.
 */
public final class ScalingResult {
    private final int effectivePlayerLevel;
    private final double horizontalDistanceFromSpawn;
    private final double distanceLevels;
    private final double heightLevels;
    private final double rawDistanceFactor;
    private final double rawHeightFactor;
    private final int tier;
    private final Map<String, AttributeFactor> attributeFactors;

    private ScalingResult(Builder b) {
        this.effectivePlayerLevel = b.effectivePlayerLevel;
        this.horizontalDistanceFromSpawn = b.horizontalDistanceFromSpawn;
        this.distanceLevels = b.distanceLevels;
        this.heightLevels = b.heightLevels;
        this.rawDistanceFactor = b.rawDistanceFactor;
        this.rawHeightFactor = b.rawHeightFactor;
        this.tier = b.tier;
        this.attributeFactors = Collections.unmodifiableMap(new LinkedHashMap<>(b.attributeFactors));
    }

    public int effectivePlayerLevel() { return effectivePlayerLevel; }
    public double horizontalDistanceFromSpawn() { return horizontalDistanceFromSpawn; }
    public double distanceLevels() { return distanceLevels; }
    public double heightLevels() { return heightLevels; }
    public double rawDistanceFactor() { return rawDistanceFactor; }
    public double rawHeightFactor() { return rawHeightFactor; }
    public int tier() { return tier; }
    public Map<String, AttributeFactor> attributeFactors() { return attributeFactors; }

    public AttributeFactor factorFor(String attribute) {
        return attributeFactors.get(attribute);
    }

    public static Builder builder() { return new Builder(); }

    public static final class AttributeFactor {
        private final double timeFactor;
        private final double distanceFactor;
        private final double heightFactor;
        private final double totalFactor;

        public AttributeFactor(double timeFactor, double distanceFactor, double heightFactor, double totalFactor) {
            this.timeFactor = timeFactor;
            this.distanceFactor = distanceFactor;
            this.heightFactor = heightFactor;
            this.totalFactor = totalFactor;
        }

        public double timeFactor() { return timeFactor; }
        public double distanceFactor() { return distanceFactor; }
        public double heightFactor() { return heightFactor; }
        public double totalFactor() { return totalFactor; }

        @Override
        public String toString() {
            return String.format("AttributeFactor{time=%.4f, dist=%.4f, height=%.4f, total=%.4f}",
                    timeFactor, distanceFactor, heightFactor, totalFactor);
        }
    }

    public static final class Builder {
        private int effectivePlayerLevel;
        private double horizontalDistanceFromSpawn;
        private double distanceLevels;
        private double heightLevels;
        private double rawDistanceFactor;
        private double rawHeightFactor;
        private int tier;
        private final Map<String, AttributeFactor> attributeFactors = new LinkedHashMap<>();

        public Builder effectivePlayerLevel(int v) { this.effectivePlayerLevel = v; return this; }
        public Builder horizontalDistanceFromSpawn(double v) { this.horizontalDistanceFromSpawn = v; return this; }
        public Builder distanceLevels(double v) { this.distanceLevels = v; return this; }
        public Builder heightLevels(double v) { this.heightLevels = v; return this; }
        public Builder rawDistanceFactor(double v) { this.rawDistanceFactor = v; return this; }
        public Builder rawHeightFactor(double v) { this.rawHeightFactor = v; return this; }
        public Builder tier(int v) { this.tier = v; return this; }
        public Builder attributeFactor(String attr, AttributeFactor f) {
            this.attributeFactors.put(attr, f);
            return this;
        }
        public Builder attributeFactor(String attr, double time, double dist, double height, double total) {
            this.attributeFactors.put(attr, new AttributeFactor(time, dist, height, total));
            return this;
        }
        public ScalingResult build() { return new ScalingResult(this); }
    }
}
