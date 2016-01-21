package com.dynatrace.diagnostics.plugins.elasticsearch;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class DerivedMeasureTest {
    @Test
    public void testDerivedMeasure() {
        DerivedMeasure measure = new DerivedMeasure(TimeUnit.SECONDS);

        // initial value
        measure.setValue(4, 4000);
        assertEquals(4, measure.getBaseMeasure().getValue(), 0.01);
        assertEquals(0, measure.getDerivedMeasure().getValue(), 0.01);

        // after one second it increased to 5, the rate is 1 per second
        measure.setValue(5, 5000);
        assertEquals(5, measure.getBaseMeasure().getValue(), 0.01);
        assertEquals(1, measure.getDerivedMeasure().getValue(), 0.01);

        // after half a second it decreased to 3, the rate is -4 per second
        measure.setValue(3, 5500);
        assertEquals(3, measure.getBaseMeasure().getValue(), 0.01);
        assertEquals(-4, measure.getDerivedMeasure().getValue(), 0.01);

        // some higher increasing
        measure.setValue(100, 7234);
        assertEquals(100, measure.getBaseMeasure().getValue(), 0.01);
        assertEquals(55.94, measure.getDerivedMeasure().getValue(), 0.01);

        // decreasing timestamp should be detected
        try {
            measure.setValue(1, 100);
            fail("Should catch exception");
        } catch (IllegalStateException e) {
            // expected here
        }
    }
}
