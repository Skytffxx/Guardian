package com.skyzzz.guardian.core.integration;

import com.skyzzz.guardian.core.GuardianPlugin;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

/**
 * Anonymous usage metrics. bStats itself honours the global opt-out in
 * plugins/bStats/config.yml, so no extra work is needed here.
 */
public final class MetricsHook {

    private static final int PLUGIN_ID = 00000; // replace with the assigned bStats id

    private final GuardianPlugin plugin;
    private Metrics metrics;

    public MetricsHook(GuardianPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        try {
            this.metrics = new Metrics(plugin, PLUGIN_ID);
            metrics.addCustomChart(new SimplePie("storage_type",
                    () -> plugin.guardianConfig().getString("storage.type", "MEMORY")));
            metrics.addCustomChart(new SimplePie("bedrock_support",
                    () -> String.valueOf(plugin.profileManager() != null)));
            metrics.addCustomChart(new SimplePie("check_count",
                    () -> String.valueOf(plugin.checkRegistry().all().size())));
        } catch (Throwable throwable) {
            plugin.getLogger().fine("bStats unavailable: " + throwable.getMessage());
        }
    }
}