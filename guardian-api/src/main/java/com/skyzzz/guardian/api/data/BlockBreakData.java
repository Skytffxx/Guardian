package com.skyzzz.guardian.api.data;

public record BlockBreakData(
        int blockX, int blockY, int blockZ,
        String material,
        boolean instantBreak,
        long timestampNanos
) {
}