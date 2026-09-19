package com.skyzzz.guardian.api;

import com.skyzzz.guardian.api.check.CheckRegistry;
import com.skyzzz.guardian.api.player.ProfileManager;
import com.skyzzz.guardian.api.violation.ViolationStore;

public final class GuardianAPI {

    public interface Guardian {
        ProfileManager profiles();
        CheckRegistry checks();
        ViolationStore violations();

        void reloadEverything();

        void runSync(Runnable task);

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