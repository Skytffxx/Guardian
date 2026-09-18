package com.skyzzz.guardian.api.player;

import java.util.Map;
import java.util.UUID;

import com.skyzzz.guardian.api.Platform;
import com.skyzzz.guardian.api.check.Check;
import com.skyzzz.guardian.api.violation.ViolationLevel;

/**
 * Per-player state. One instance per online player, created on join and discarded
 * on quit — no static maps scattered across checks.
 */
public interface PlayerProfile {

    UUID uuid();

    String name();

    Platform platform();

    void setPlatform(Platform platform);

    /** True when the player is on Geyser/Floodgate. */
    default boolean isBedrock() {
        return platform() == Platform.BEDROCK;
    }

    /**
     * Multiplier applied to every timing/angle threshold. Java = 1.0; Bedrock gets a
     * configurable leniency factor from config so touch input is not punished.
     */
    double thresholdScale();

    int ping();

    void setPing(int ping);

    int clientVersion();

    void setClientVersion(int clientVersion);

    /** Monotonic server tick counter for this profile. */
    long tick();

    void advanceTick();

    PositionHistory positions();

    ClickHistory clicks();

    ViolationLevel violations(String checkName);

    Map<String, Double> violationSnapshot();

    /** Adds VL and returns the new total. Fires {@code GuardianFlagEvent}. */
    double flag(Check check, double weight, String debug, Object... args);

    /** Applies decay credit for clean behaviour. */
    void reward(Check check, double amount);

    boolean isExempt(Check check);

    void setExempt(String reason, boolean exempt);

    boolean debugEnabled(String checkName);

    void setDebug(String checkName, boolean enabled);

    void sendDebug(String message);
    
    /** Free-form per-tick gameplay state populated by core (potions, TPS, liquids, …). */
    Object attribute(String key);

    void setAttribute(String key, Object value);

    void reset();
}