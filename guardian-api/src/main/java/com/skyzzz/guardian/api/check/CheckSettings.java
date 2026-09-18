package com.skyzzz.guardian.api.check;

import java.util.List;

/**
 * Read-only view over the {@code checks.<category>.<check>} config section.
 * Core supplies a Bukkit-backed implementation; the API never sees Bukkit.
 */
public interface CheckSettings {

    boolean enabled();

    double getDouble(String key, double def);

    int getInt(String key, int def);

    boolean getBoolean(String key, boolean def);

    String getString(String key, String def);

    List<String> getStringList(String key);

    /** Applies the Bedrock threshold multiplier when the player is on Geyser. */
    default double scaled(String key, double base, double scale) {
        return getDouble(key, base) * scale;
    }
}