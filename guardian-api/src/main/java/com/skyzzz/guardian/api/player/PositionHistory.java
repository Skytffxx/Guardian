package com.skyzzz.guardian.api.player;

/**
 * Fixed-capacity ring buffer of the most recent positions. Not thread-safe by design:
 * all writes happen on the player's tick thread.
 */
public final class PositionHistory {

    private final PositionSample[] buffer;
    private int cursor = -1;
    private int size;

    public PositionHistory(int capacity) {
        if (capacity < 2) {
            throw new IllegalArgumentException("capacity must be >= 2");
        }
        this.buffer = new PositionSample[capacity];
    }

    public void add(PositionSample sample) {
        cursor = (cursor + 1) % buffer.length;
        buffer[cursor] = sample;
        if (size < buffer.length) {
            size++;
        }
    }

    /** {@code back == 0} is the newest sample. Returns null when out of range. */
    public PositionSample get(int back) {
        if (back < 0 || back >= size) {
            return null;
        }
        int index = ((cursor - back) % buffer.length + buffer.length) % buffer.length;
        return buffer[index];
    }

    public PositionSample latest() {
        return get(0);
    }

    public PositionSample previous() {
        return get(1);
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return buffer.length;
    }

    public void clear() {
        cursor = -1;
        size = 0;
        for (int idx = 0; idx < buffer.length; idx++) {
            buffer[idx] = null;
        }
    }

    /**
     * Mean horizontal blocks/tick over the last {@code ticks} samples.
     * Returns 0 when there is not enough history.
     */
    public double averageHorizontalSpeed(int ticks) {
        if (size < ticks + 1 || ticks <= 0) {
            return 0.0D;
        }
        double total = 0.0D;
        for (int back = 0; back < ticks; back++) {
            PositionSample current = get(back);
            PositionSample older = get(back + 1);
            double dx = current.x() - older.x();
            double dz = current.z() - older.z();
            total += Math.sqrt(dx * dx + dz * dz);
        }
        return total / ticks;
    }

    /** Mean vertical delta/tick over the last {@code ticks} samples (signed). */
    public double averageVerticalSpeed(int ticks) {
        if (size < ticks + 1 || ticks <= 0) {
            return 0.0D;
        }
        double total = 0.0D;
        for (int back = 0; back < ticks; back++) {
            total += get(back).y() - get(back + 1).y();
        }
        return total / ticks;
    }
}