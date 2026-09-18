package com.skyzzz.guardian.core.config;

import com.skyzzz.guardian.core.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class Messages {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration configuration;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
    }

    public void load() {
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            plugin.getLogger().warning("Could not create messages folder");
        }
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.configuration = YamlConfiguration.loadConfiguration(file);
        try (InputStream defaults = plugin.getResource("messages.yml")) {
            if (defaults != null) {
                this.configuration.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaults, StandardCharsets.UTF_8)));
                this.configuration.options().copyDefaults(true);
            }
        } catch (IOException ignored) {
            // defaults are optional
        }
    }

    public String raw(String key) {
        return configuration.getString(key, "");
    }

    public Component component(String key) {
        return Text.miniMessage(raw(key));
    }

    public Component format(String key, Object... args) {
        String template = raw(key);
        return Text.miniMessage(args.length == 0 ? template : String.format(template, args));
    }
}