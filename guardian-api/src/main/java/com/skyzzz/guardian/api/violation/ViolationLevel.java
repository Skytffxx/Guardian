package com.skyzzz.guardian.api.violation;

/**
 * A single check's violation level with time-based decay. Punishments key off this
 * value, never off an individual flag.
 */
public final class ViolationLevel {

    private static final double CEILING = 1000.0D;

    private final double decayPerSecond;
    private double value;
    private long lastUpdateNanos;

    public ViolationLevel(double decayPerSecond) {
        this.decayPerSecond = Math.max(0.0D, decayPerSecond);
        this.lastUpdateNanos = System.nanoTime();
    }

    public synchronized double value() {
        applyDecay();
        return value;
    }

    public synchronized double add(double amount) {
        applyDecay();
        value = Math.min(CEILING, value + Math.max(0.0D, amount));
        return value;
    }

    public synchronized void subtract(double amount) {
        applyDecay();
        value = Math.max(0.0D, value - Math.max(0.0D, amount));
    }

    public synchronized void reset() {
        value = 0.0D;
        lastUpdateNanos = System.nanoTime();
    }

    public double decayPerSecond() {
        return decayPerSecond;
    }

    private void applyDecay() {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastUpdateNanos) / 1_000_000_000.0D;
        if (elapsedSeconds <= 0.0D) {
            return;
        }
        lastUpdateNanos = now;
        if (value > 0.0D) {
            value = Math.max(0.0D, value - elapsedSeconds * decayPerSecond);
        }
    }
}