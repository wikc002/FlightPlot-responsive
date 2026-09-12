package me.drton.flightplot;

import java.util.ArrayList;

/**
 * Created by ton on 09.03.15.
 */
public class Series extends ArrayList<XYPoint> implements PlotItem {
    private final String title;
    private final double skipOut;
    private boolean bucketOpen;
    private double bucketStart, firstTime, firstValue, minTime, minValue, maxTime, maxValue, lastTime, lastValue;
    private double lastGapTime = Double.NEGATIVE_INFINITY;

    public Series(String title, double skipOut) {
        this.title = title;
        this.skipOut = skipOut;
    }

    @Override
    public String getTitle() {
        return title;
    }

    public String getFullTitle(String processorTitle) {
        return processorTitle + (title.isEmpty() ? "" : (":" + title));
    }

    public void addPoint(double time, double value) {
        if (!Double.isFinite(time)) return;
        if (!Double.isFinite(value)) {
            // Missing sensor data can span millions of samples. Keep bounded gap markers.
            if (time-lastGapTime >= skipOut || time < lastGapTime || skipOut <= 0) {
                finish();
                if (isEmpty() || Double.isFinite(get(size()-1).y)) add(new XYPoint(time,Double.NaN));
                lastGapTime=time;
            }
            return;
        }
        if (skipOut <= 0) {
            finish(); add(new XYPoint(time, value)); return;
        }
        if (bucketOpen && (time - bucketStart >= skipOut || time < lastTime)) finish();
        if (!bucketOpen) {
            bucketOpen = true; bucketStart = time;
            firstTime = minTime = maxTime = lastTime = time;
            firstValue = minValue = maxValue = lastValue = value;
        } else {
            lastTime = time; lastValue = value;
            if (value < minValue) { minValue = value; minTime = time; }
            if (value > maxValue) { maxValue = value; maxTime = time; }
        }
    }

    /** Preserve first, extrema and last in chronological order, including the final bucket. */
    public void finish() {
        if (!bucketOpen) return;
        add(new XYPoint(firstTime, firstValue));
        if (minTime <= maxTime) { appendDistinct(minTime, minValue); appendDistinct(maxTime, maxValue); }
        else { appendDistinct(maxTime, maxValue); appendDistinct(minTime, minValue); }
        appendDistinct(lastTime, lastValue);
        bucketOpen = false;
    }
    private void appendDistinct(double t, double v) {
        XYPoint previous = get(size()-1);
        if (previous.x != t || previous.y != v) add(new XYPoint(t,v));
    }
}
