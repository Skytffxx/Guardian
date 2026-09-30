package com.skyzzz.guardian.core.integration;

import com.skyzzz.guardian.core.GuardianPlugin;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects Bedrock players through the Geyser/Floodgate API without a hard dependency.
 * With neither plugin present there are no Bedrock players, so everyone is Java.
 */
public final class FloodgateHook {

    /** Floodgate lookups are reflective, so results are cached briefly. */
    private static final long CACHE_MILLIS = 30_000L;

    private record Cached(boolean bedrock, long checkedAt) {
    }

    private final GuardianPlugin plugin;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    private Object floodgateApi;
    private Method isFloodgatePlayer;
    private boolean available;
    private boolean geyserStandalone;

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
            // Fall through to the Geyser standalone check below.
        }
        // Geyser-Spigot without Floodgate has no FloodgateApi, so Bedrock players are
        // resolved from the Geyser connection list instead.
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
        Cached cached = cache.get(uuid);
        if (cached != null && now - cached.checkedAt() < CACHE_MILLIS) {
            return cached.bedrock();
        }
        boolean result = resolveBedrock(uuid);
        cache.put(uuid, new Cached(result, now));
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

    /** Drops the cache entry on quit so the map cannot grow unboundedly. */
    public void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    public boolean available() {
        return available;
    }
}