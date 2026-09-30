package com.skyzzz.guardian.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;

/**
 * Owns config.yml. Every getter takes an explicit default, so a missing key falls back
 * to the documented value instead of throwing.
 */
public final class GuardianConfig {

    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration configuration;

    public GuardianConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "config.yml");
    }

    public void load() {
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            plugin.getLogger().warning("Could not create Guardian data folder");
        }
        if (!file.exists()) {
            plugin.saveResource("config.yml", false);
        }

        configuration = YamlConfiguration.loadConfiguration(file);

        // Defaults merge keeps keys added by newer versions visible to existing configs.
        try (InputStream defaults = plugin.getResource("config.yml")) {
            if (defaults != null) {
                configuration.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaults, StandardCharsets.UTF_8)));
                configuration.options().copyDefaults(true);
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not load config defaults", exception);
        }
    }

    public void save() {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config.yml", exception);
        }
    }

    public FileConfiguration raw() {
        return configuration;
    }

    public ConfigurationSection section(String path) {
        ConfigurationSection existing = configuration.getConfigurationSection(path);
        if (existing != null) {
            return existing;
        }
        return configuration.createSection(path);
    }

    public String getString(String path, String def) {
        return configuration.getString(path, def);
    }

    public double getDouble(String path, double def) {
        return configuration.getDouble(path, def);
    }

    public int getInt(String path, int def) {
        return configuration.getInt(path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        return configuration.getBoolean(path, def);
    }

    public List<String> getStringList(String path) {
        return configuration.getStringList(path);
    }

    public void set(String path, Object value) {
        configuration.set(path, value);
    }
}