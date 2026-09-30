package com.skyzzz.guardian.api.player;

import java.util.Map;
import java.util.UUID;

import com.skyzzz.guardian.api.Platform;
import com.skyzzz.guardian.api.check.Check;
import com.skyzzz.guardian.api.violation.ViolationLevel;

/** Per-player state: one instance per online player, alive from join to quit. */
public interface PlayerProfile {

    UUID uuid();

    String name();

    Platform platform();

    void setPlatform(Platform platform);

    /** True for Geyser/Floodgate clients. */
    default boolean isBedrock() {
        return platform() == Platform.BEDROCK;
    }

    /**
     * Multiplier on every timing and angle threshold: 1.0 on Java, a configurable
     * leniency factor on Bedrock so touch input is not punished.
     */
    double thresholdScale();

    int ping();

    void setPing(int ping);

    int clientVersion();

    void setClientVersion(int clientVersion);

    /** Monotonic server tick counter. */
    long tick();

    void advanceTick();

    PositionHistory positions();

    ClickHistory clicks();

    ViolationLevel violations(String checkName);

    Map<String, Double> violationSnapshot();

    /** Adds VL and returns the new total. */
    double flag(Check check, double weight, String debug, Object... args);

    /** Decay credit for clean play. */
    void reward(Check check, double amount);

    boolean isExempt(Check check);

    void setExempt(String reason, boolean exempt);

    boolean debugEnabled(String checkName);

    void setDebug(String checkName, boolean enabled);

    void sendDebug(String message);

    /** Free-form per-tick state written by core and read by checks. */
    Object attribute(String key);

    void setAttribute(String key, Object value);

    void reset();
}