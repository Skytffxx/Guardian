package com.skyzzz.guardian.api.player;

public record PositionSample(
        double x, double y, double z,
        float yaw, float pitch,
        boolean onGround,
        long timestampNanos
) {
}