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

    /** Cache TTL: re-check at most this often per player (Floodgate calls are reflective). */
    private static final long CACHE_MILLIS = 30_000L;

    private final GuardianPlugin plugin;
    private Object floodgateApi;
    private Method isFloodgatePlayer;
    private boolean available;
    private boolean geyserStandalone;
    private final java.util.Map<java.util.UUID, CachedResult> cache =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final class CachedResult {
        final boolean bedrock;
        final long checkedAt;

        CachedResult(boolean bedrock, long checkedAt) {
            this.bedrock = bedrock;
            this.checkedAt = checkedAt;
        }
    }

    public FloodgateHook(GuardianPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        available = false;
        geyserStandalone = false;
        floodgateApi = null;
        isFloodgatePlayer = null;
        cache.clear();

        if (plugin.getServer().getPluginManager().getPlugin("floodgate") == null
                && plugin.getServer().getPluginManager().getPlugin("Geyser-Spigot") == null) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstance = apiClass.getMethod("getInstance");
            floodgateApi = getInstance.invoke(null);
            isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            available = true;
            return;
        } catch (Throwable ignored) {
            // fall through to Geyser standalone detection
        }
        // Geyser-Spigot without Floodgate: bedrock players join via the Geyser connection
        // list rather than FloodgateApi. Detect reflectively so there is no hard dep.
        try {
            Class.forName("org.geysermc.geyser.api.GeyserApi");
            geyserStandalone = true;
            available = true;
        } catch (Throwable throwable) {
            plugin.getLogger().info("Floodgate/Geyser present but not usable; "
                    + "Bedrock profiles will not be applied.");
        }
    }

    public boolean isBedrockPlayer(UUID uuid) {
        if (!available) {
            return false;
        }
        long now = System.currentTimeMillis();
        CachedResult cached = cache.get(uuid);
        if (cached != null && now - cached.checkedAt < CACHE_MILLIS) {
            return cached.bedrock;
        }
        boolean result = resolveBedrock(uuid);
        cache.put(uuid, new CachedResult(result, now));
        return result;
    }

    private boolean resolveBedrock(UUID uuid) {
        if (isFloodgatePlayer != null) {
            try {
                Object result = isFloodgatePlayer.invoke(floodgateApi, uuid);
                return result instanceof Boolean bool && bool;
            } catch (Throwable ignored) {
                return false;
            }
        }
        if (geyserStandalone) {
            try {
                Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                Object api = apiClass.getMethod("api").invoke(null);
                Object connection = api.getClass()
                        .getMethod("connectionByUuid", UUID.class).invoke(api, uuid);
                return connection != null;
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    /** Drop a cached entry on quit so the map cannot grow unboundedly. */
    public void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    public boolean available() {
        return available;
    }
}