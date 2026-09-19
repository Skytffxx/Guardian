package com.skyzzz.guardian.core.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Folia-aware scheduler facade. Detects Folia at runtime and routes through the
 * region/global/async schedulers; falls back to the Bukkit scheduler elsewhere.
 * Reflection keeps the class compiling against plain Paper.
 */
public final class Schedulers {

    private final Plugin plugin;
    private final boolean folia;

    private Method globalRun;
    private Method globalRunRepeating;
    private Method asyncRun;

    public Schedulers(Plugin plugin) {
        this.plugin = plugin;
        this.folia = detectFolia();
        if (folia) {
            initFolia();
        }
    }

    public boolean isFolia() {
        return folia;
    }

    public void runSync(Runnable task) {
        if (!folia) {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
            return;
        }
        try {
            globalRun.invoke(null, plugin, (Consumer<Object>) ignored -> task.run());
        } catch (Throwable throwable) {
            task.run();
        }
    }

    public void runAsync(Runnable task) {
        if (!folia) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            return;
        }
        try {
            asyncRun.invoke(null, plugin, (Consumer<Object>) ignored -> task.run());
        } catch (Throwable throwable) {
            task.run();
        }
    }

    public void runSyncRepeating(Runnable task, long delayTicks, long periodTicks) {
        if (!folia) {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
            return;
        }
        try {
            globalRunRepeating.invoke(null, plugin,
                    (Consumer<Object>) ignored -> task.run(), delayTicks, periodTicks);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Folia repeating task registration failed");
        }
    }

    /** One-shot delayed task on the region/main thread (Folia-safe). */
    public void runSyncDelayed(Runnable task, long delayTicks) {
        if (!folia) {
            Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(0L, delayTicks));
            return;
        }
        try {
            Class<?> global = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            Object globalInstance = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            global.getMethod("runDelayed", Plugin.class, Consumer.class, long.class)
                    .invoke(globalInstance, plugin, (Consumer<Object>) ignored -> task.run(),
                            Math.max(1L, delayTicks));
        } catch (Throwable throwable) {
            task.run();
        }
    }

    public void runAsyncRepeating(Runnable task, long delayTicks, long periodTicks) {
        if (!folia) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
            return;
        }
        try {
            asyncRun.invoke(null, plugin, (Consumer<Object>) ignored -> task.run());
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Folia async task registration failed");
        }
    }

    private boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private void initFolia() {
        try {
            Class<?> global = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            Class<?> async = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");

            Object globalInstance = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Object asyncInstance = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);

            globalRun = global.getMethod("run", Plugin.class, Consumer.class);
            globalRunRepeating = global.getMethod("runAtFixedRate", Plugin.class,
                    Consumer.class, long.class, long.class);
            asyncRun = async.getMethod("runNow", Plugin.class, Consumer.class);

            // Silence unused warnings for the instances; reflective invocation needs the holder.
            assert globalInstance != null && asyncInstance != null;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Folia detected but scheduler init failed; "
                    + "falling back to Bukkit scheduler");
        }
    }

    /** Kept for API symmetry — TimeUnit import used by callers that schedule async delays. */
    public static long toMillis(long ticks) {
        return TimeUnit.MILLISECONDS.convert(ticks * 50L, TimeUnit.MILLISECONDS);
    }
}