package com.skyzzz.guardian.api;

import com.skyzzz.guardian.api.check.CheckRegistry;
import com.skyzzz.guardian.api.player.ProfileManager;
import com.skyzzz.guardian.api.violation.ViolationStore;

/**
 * Static service locator. Core registers itself on enable; checks, add-ons and
 * integrations resolve services through here without a compile-time link to core.
 */
public final class GuardianAPI {

    /** Implemented by the core plugin. */
    public interface Guardian {
        ProfileManager profiles();
        CheckRegistry checks();
        ViolationStore violations();

        /** Reloads config.yml + messages.yml and re-binds every check's settings. */
        void reloadEverything();

        /** Runs {@code command} on the main thread (or the correct Folia region thread). */
        void runSync(Runnable task);

        /** Runs {@code command} off-thread. */
        void runAsync(Runnable task);
    }

    private static volatile Guardian instance;

    private GuardianAPI() {
    }

    public static void register(Guardian guardian) {
        if (instance != null) {
            throw new IllegalStateException("GuardianAPI already registered");
        }
        instance = guardian;
    }

    public static void unregister() {
        instance = null;
    }

    public static Guardian get() {
        Guardian local = instance;
        if (local == null) {
            throw new IllegalStateException("Guardian is not loaded");
        }
        return local;
    }

    public static boolean isLoaded() {
        return instance != null;
    }
}