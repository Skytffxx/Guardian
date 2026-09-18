package com.skyzzz.guardian.api.data;

/**
 * Immutable snapshot of one movement packet. All derived values are pre-computed
 * so checks stay allocation-free on the hot path.
 */
public record MoveData(
        double x, double y, double z,
        double lastX, double lastY, double lastZ,
        float yaw, float pitch,
        float lastYaw, float lastPitch,
        boolean onGround, boolean lastOnGround,
        boolean positionChanged, boolean rotationChanged,
        double eyeHeight,
        long timestampNanos
) {

    public double deltaX() {
        return x - lastX;
    }

    public double deltaY() {
        return y - lastY;
    }

    public double deltaZ() {
        return z - lastZ;
    }

    public double horizontalDistance() {
        double dx = deltaX();
        double dz = deltaZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public double verticalDistance() {
        return Math.abs(deltaY());
    }

    public double threeDimensionalDistance() {
        double dx = deltaX();
        double dy = deltaY();
        double dz = deltaZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public float deltaYaw() {
        return wrapDegrees(yaw - lastYaw);
    }

    public float deltaPitch() {
        return pitch - lastPitch;
    }

    public float yawDeltaAbs() {
        return Math.abs(deltaYaw());
    }

    public float pitchDeltaAbs() {
        return Math.abs(deltaPitch());
    }

    public static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }
}