package com.skyzzz.guardian.core.integration;

import com.skyzzz.guardian.core.GuardianPlugin;
import com.skyzzz.guardian.core.check.CheckRegistryImpl;
import com.skyzzz.guardian.core.player.GuardianProfileManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * Exposes Guardian state to PlaceholderAPI:
 *   %guardian_vl_total%     total VL across all checks
 *   %guardian_vl_<check>%   VL for a single check
 *   %guardian_platform%     JAVA or BEDROCK
 *   %guardian_checks%       number of registered checks
 *   %guardian_enabled_<check>%  true/false
 */
public final class PlaceholderHook {

    private PlaceholderHook() {
    }

    public static void register(GuardianPlugin plugin, GuardianProfileManager profiles,
                                CheckRegistryImpl registry) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            new Expansion(plugin, profiles, registry).register();
        } catch (Throwable throwable) {
            plugin.getLogger().warning("PlaceholderAPI expansion failed to register: "
                    + throwable.getMessage());
        }
    }

    private static final class Expansion extends me.clip.placeholderapi.expansion.PlaceholderExpansion {

        private final GuardianPlugin plugin;
        private final GuardianProfileManager profiles;
        private final CheckRegistryImpl registry;

        Expansion(GuardianPlugin plugin, GuardianProfileManager profiles, CheckRegistryImpl registry) {
            this.plugin = plugin;
            this.profiles = profiles;
            this.registry = registry;
        }

        @Override
        public String getIdentifier() {
            return "guardian";
        }

        @Override
        public String getAuthor() {
            return "skyzzz";
        }

        @Override
        public String getVersion() {
            return plugin.getPluginMeta().getVersion();
        }

        @Override
        public String onRequest(OfflinePlayer player, String params) {
            if (player == null || player.getUniqueId() == null) {
                return "";
            }
            var profile = profiles.get(player.getUniqueId());
            if (profile == null) {
                return "";
            }

            if (params.equalsIgnoreCase("vl_total")) {
                double total = 0.0D;
                for (Double value : profile.violationSnapshot().values()) {
                    total += value;
                }
                return String.format("%.2f", total);
            }
            if (params.equalsIgnoreCase("platform")) {
                return profile.platform().name();
            }
            if (params.equalsIgnoreCase("checks")) {
                return String.valueOf(registry.all().size());
            }
            if (params.toLowerCase().startsWith("vl_")) {
                String checkName = params.substring(3).toLowerCase();
                return String.format("%.2f",
                        profile.violationSnapshot().getOrDefault(checkName, 0.0D));
            }
            if (params.toLowerCase().startsWith("enabled_")) {
                String checkName = params.substring(8);
                return registry.get(checkName).map(check -> String.valueOf(check.isEnabled())).orElse("unknown");
            }
            return null;
        }
    }
}