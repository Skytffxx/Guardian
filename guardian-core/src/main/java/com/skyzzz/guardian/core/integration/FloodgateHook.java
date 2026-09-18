package com.skyzzz.guardian.core.integration;

import com.skyzzz.guardian.core.GuardianPlugin;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Detects Bedrock players through the Geyser/Floodgate API without a hard dependency.
 * When Floodgate is absent, everyone is treated as Java — which is correct, because
 * without Geyser there are no Bedrock players.
 */
public final class FloodgateHook {

    private final GuardianPlugin plugin;
    private Object floodgateApi;
    private Method isFloodgatePlayer;
    private boolean available;

    public FloodgateHook(GuardianPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        available = false;
        floodgateApi = null;
        isFloodgatePlayer = null;

        if (plugin.getServer().getPluginManager().getPlugin("floodgate") == null) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstance = apiClass.getMethod("getInstance");
            floodgateApi = getInstance.invoke(null);
            isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            available = true;
        } catch (Throwable throwable) {
            plugin.getLogger().info("Floodgate present but not usable; "
                    + "Bedrock profiles will not be applied.");
        }
    }

    public boolean isBedrockPlayer(UUID uuid) {
        if (!available) {
            return false;
        }
        try {
            Object result = isFloodgatePlayer.invoke(floodgateApi, uuid);
            return result instanceof Boolean bool && bool;
        } catch (Throwable throwable) {
            return false;
        }
    }

    public boolean available() {
        return available;
    }
}