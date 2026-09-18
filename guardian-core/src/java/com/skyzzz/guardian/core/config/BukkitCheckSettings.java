package com.skyzzz.guardian.core.config;

import com.skyzzz.guardian.api.check.CheckSettings;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Objects;

public final class BukkitCheckSettings implements CheckSettings {

    private final ConfigurationSection section;
    private final GuardianConfig config;

    public BukkitCheckSettings(ConfigurationSection section, GuardianConfig config) {
        this.section = Objects.requireNonNull(section, "section");
        this.config = config;
    }

    @Override
    public boolean enabled() {
        return section.getBoolean("enabled", true);
    }

    @Override
    public double getDouble(String key, double def) {
        return section.getDouble(key, def);
    }

    @Override
    public int getInt(String key, int def) {
        return section.getInt(key, def);
    }

    @Override
    public boolean getBoolean(String key, boolean def) {
        return section.getBoolean(key, def);
    }

    @Override
    public String getString(String key, String def) {
        return section.getString(key, def);
    }

    @Override
    public List<String> getStringList(String key) {
        return section.getStringList(key);
    }

    /** Global Bedrock multiplier, overridable per check. */
    public double bedrockMultiplier() {
        return section.getDouble("bedrock-threshold-multiplier",
                config.getDouble("false-positives.bedrock-threshold-multiplier", 1.35D));
    }
}