package com.skyzzz.guardian.api.player;

/**
 * Rolling click history. Stores inter-click intervals (ms) for the last N clicks,
 * which is what autoclicker statistics actually need.
 */
public final class ClickHistory {

    private final long[] intervals;
    private int cursor = -1;
    private int size;
    private long lastClickNanos;

    public ClickHistory(int capacity) {
        this.intervals = new long[Math.max(2, capacity)];
    }

    /** Returns the interval in ms since the previous click, or -1 if this is the first. */
    public long record(long timestampNanos) {
        if (lastClickNanos == 0L) {
            lastClickNanos = timestampNanos;
            return -1L;
        }
        long intervalMs = (timestampNanos - lastClickNanos) / 1_000_000L;
        lastClickNanos = timestampNanos;
        cursor = (cursor + 1) % intervals.length;
        intervals[cursor] = intervalMs;
        if (size < intervals.length) {
            size++;
        }
        return intervalMs;
    }

    public int size() {
        return size;
    }

    public boolean isFull() {
        return size == intervals.length;
    }

    public void clear() {
        cursor = -1;
        size = 0;
        lastClickNanos = 0L;
    }

    /** Newest-first interval at {@code back}, or -1 when out of range. */
    public long interval(int back) {
        if (back < 0 || back >= size) {
            return -1L;
        }
        int index = ((cursor - back) % intervals.length + intervals.length) % intervals.length;
        return intervals[index];
    }

    public double mean() {
        if (size == 0) {
            return 0.0D;
        }
        double total = 0.0D;
        for (int idx = 0; idx < size; idx++) {
            total += interval(idx);
        }
        return total / size;
    }

    public double standardDeviation() {
        if (size < 2) {
            return 0.0D;
        }
        double mean = mean();
        double sum = 0.0D;
        for (int idx = 0; idx < size; idx++) {
            double diff = interval(idx) - mean;
            sum += diff * diff;
        }
        return Math.sqrt(sum / (size - 1));
    }

    /** Coefficient of variation: stdev / mean. Humans sit well above 0; macroers near 0. */
    public double coefficientOfVariation() {
        double mean = mean();
        return mean <= 0.0D ? 0.0D : standardDeviation() / mean;
    }

    /** Counts how many intervals are within {@code toleranceMs} of another interval. */
    public int duplicateIntervalCount(long toleranceMs) {
        int duplicates = 0;
        for (int a = 0; a < size; a++) {
            for (int b = a + 1; b < size; b++) {
                if (Math.abs(interval(a) - interval(b)) <= toleranceMs) {
                    duplicates++;
                    break;
                }
            }
        }
        return duplicates;
    }
}