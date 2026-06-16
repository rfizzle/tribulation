package com.rfizzle.tribulation;

import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.scaling.ScalingEngine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScalingModeResolutionTest {

    @Test
    public void testMaxMode() {
        List<Integer> levels = List.of(50, 200, 100);
        int result = ScalingEngine.foldLevels(TribulationConfig.ScalingMode.MAX, levels);
        assertEquals(200, result);
    }

    @Test
    public void testAverageMode() {
        List<Integer> levels = List.of(50, 200);
        int result = ScalingEngine.foldLevels(TribulationConfig.ScalingMode.AVERAGE, levels);
        assertEquals(125, result);

        levels = List.of(50, 100, 150);
        result = ScalingEngine.foldLevels(TribulationConfig.ScalingMode.AVERAGE, levels);
        assertEquals(100, result);

        // Test flooring
        levels = List.of(10, 20, 25); // sum 55, size 3, avg 18.33
        result = ScalingEngine.foldLevels(TribulationConfig.ScalingMode.AVERAGE, levels);
        assertEquals(18, result);
    }

    @Test
    public void testNearestMode() {
        // foldLevels for NEAREST just returns the first element if not empty
        List<Integer> levels = List.of(50, 200);
        int result = ScalingEngine.foldLevels(TribulationConfig.ScalingMode.NEAREST, levels);
        assertEquals(50, result);
    }

    @Test
    public void testEmptyList() {
        List<Integer> levels = List.of();
        int result = ScalingEngine.foldLevels(TribulationConfig.ScalingMode.MAX, levels);
        assertEquals(0, result);

        result = ScalingEngine.foldLevels(TribulationConfig.ScalingMode.AVERAGE, levels);
        assertEquals(0, result);

        result = ScalingEngine.foldLevels(TribulationConfig.ScalingMode.NEAREST, levels);
        assertEquals(0, result);
    }
}
