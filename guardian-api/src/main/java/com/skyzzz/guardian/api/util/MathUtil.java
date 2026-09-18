package com.skyzzz.guardian.api.util;

public final class MathUtil {

    private MathUtil() {
    }

    /** Squared distance from a point to an axis-aligned box (0 when inside). */
    public static double distanceSquaredToBox(double px, double py, double pz,
                                              double minX, double minY, double minZ,
                                              double maxX, double maxY, double maxZ) {
        double dx = Math.max(Math.max(minX - px, 0.0D), px - maxX);
        double dy = Math.max(Math.max(minY - py, 0.0D), py - maxY);
        double dz = Math.max(Math.max(minZ - pz, 0.0D), pz - maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    public static double distanceToBox(double px, double py, double pz,
                                       double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ) {
        return Math.sqrt(distanceSquaredToBox(px, py, pz, minX, minY, minZ, maxX, maxY, maxZ));
    }

    /**
     * Greatest common divisor of two doubles. Used by the aim check to detect
     * rotation deltas that are all exact multiples of a fixed sensitivity step.
     */
    public static double gcd(double a, double b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b > 1.0E-4D) {
            double temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double round(double value, int places) {
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }
}