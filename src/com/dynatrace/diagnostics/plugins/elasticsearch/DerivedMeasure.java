package com.dynatrace.diagnostics.plugins.elasticsearch;

import com.google.common.base.Preconditions;

import java.util.concurrent.TimeUnit;

/**
 * A helper class for computing derived values, i.e. if you
 * get an increasing count of operations, you can use this
 * class to compute a rate of operations per time-unit, e.g. seconds.
 *
 * In order for this to work you need to keep a member of this
 * class across calls to the Dynatrace monitor and use the same instance
 * for recording values so that the previous value can be kept in memory.
 */
public class DerivedMeasure {
    private final TimeUnit timeUnit;

    private double previousValue = 0;
    private long previousValueTS = 0;
    private double value = 0;
    private long valueTS = 0;

    public DerivedMeasure(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public Measure getBaseMeasure() {
        return measure(value);
    }

    public Measure getDerivedMeasure() {
        // no derived value if we do not have a previous value
        if(previousValueTS == 0) {
            return measure(0);
        }

        long duration = valueTS - previousValueTS;
        double diff = value - previousValue;

        double timeDivisor = ((double)duration)/timeUnit.toMillis(1);
        double rate = diff/timeDivisor;

        return measure(rate);
    }

    private Measure measure(double rate) {
        Measure measure = new Measure();
        measure.setValue(rate);
        return measure;
    }

    public void setValue(double value, long valueTS) {
        // verify that timestamps are only increasing with a small "grace time" as System.currentTimeMillis() sometimes runs backwards a bit!
        Preconditions.checkState(previousValueTS == 0 || (valueTS + 100 > previousValueTS));

        // roll over to the new "previous" value
        previousValue = this.value;
        previousValueTS = this.valueTS;

        // then store the new value
        this.value = value;
        this.valueTS = valueTS;
    }
}
