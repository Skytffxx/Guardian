package com.skyzzz.guardian.api.data;

public record BlockPlaceData(
        int blockX, int blockY, int blockZ,
        String material,
        double eyeX, double eyeY, double eyeZ,
        float yaw, float pitch,
        int faceId,
        double eyeDistance,
        boolean lineOfSight,
        long timestampNanos
) {
}