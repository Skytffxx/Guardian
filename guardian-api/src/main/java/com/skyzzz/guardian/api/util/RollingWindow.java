package com.skyzzz.guardian.api.util;

/**
 * Fixed-size numeric window with O(1) insert and O(n) aggregates. Used by the
 * rolling-window evaluation strategy: nothing flags on a single sample.
 */
public final class RollingWindow {

    private final double[] values;
    private int cursor = -1;
    private int size;
    private double sum;

    public RollingWindow(int capacity) {
        this.values = new double[Math.max(2, capacity)];
    }

    public void add(double value) {
        cursor = (cursor + 1) % values.length;
        if (size == values.length) {
            sum -= values[cursor];
        } else {
            size++;
        }
        values[cursor] = value;
        sum += value;
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return values.length;
    }

    public boolean isFull() {
        return size == values.length;
    }

    public void clear() {
        cursor = -1;
        size = 0;
        sum = 0.0D;
        for (int idx = 0; idx < values.length; idx++) {
            values[idx] = 0.0D;
        }
    }

    public double mean() {
        return size == 0 ? 0.0D : sum / size;
    }

    public double value(int back) {
        if (back < 0 || back >= size) {
            return Double.NaN;
        }
        int index = ((cursor - back) % values.length + values.length) % values.length;
        return values[index];
    }

    public double sum() {
        return sum;
    }

    public double max() {
        double max = Double.NEGATIVE_INFINITY;
        for (int idx = 0; idx < size; idx++) {
            max = Math.max(max, value(idx));
        }
        return size == 0 ? 0.0D : max;
    }

    public double min() {
        double min = Double.POSITIVE_INFINITY;
        for (int idx = 0; idx < size; idx++) {
            min = Math.min(min, value(idx));
        }
        return size == 0 ? 0.0D : min;
    }

    public double standardDeviation() {
        if (size < 2) {
            return 0.0D;
        }
        double mean = mean();
        double total = 0.0D;
        for (int idx = 0; idx < size; idx++) {
            double diff = value(idx) - mean;
            total += diff * diff;
        }
        return Math.sqrt(total / (size - 1));
    }

    /** Number of samples strictly above {@code threshold}. */
    public int countAbove(double threshold) {
        int count = 0;
        for (int idx = 0; idx < size; idx++) {
            if (value(idx) > threshold) {
                count++;
            }
        }
        return count;
    }

    /** Longest run of consecutive (newest-first) samples above {@code threshold}. */
    public int longestRunAbove(double threshold) {
        int best = 0;
        int current = 0;
        for (int back = size - 1; back >= 0; back--) {
            if (value(back) > threshold) {
                current++;
                best = Math.max(best, current);
            } else {
                current = 0;
            }
        }
        return best;
    }
}