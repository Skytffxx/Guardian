package com.skyzzz.guardian.core;

import com.skyzzz.guardian.api.GuardianAPI;
import com.skyzzz.guardian.api.check.Check;
import com.skyzzz.guardian.api.check.CheckRegistry;
import com.skyzzz.guardian.api.player.ProfileManager;
import com.skyzzz.guardian.api.violation.ViolationStore;
import com.skyzzz.guardian.checks.CheckBootstrap;
import com.skyzzz.guardian.core.alert.AlertManager;
import com.skyzzz.guardian.core.check.CheckRegistryImpl;
import com.skyzzz.guardian.core.command.GuardianCommand;
import com.skyzzz.guardian.core.config.GuardianConfig;
import com.skyzzz.guardian.core.config.Messages;
import com.skyzzz.guardian.core.integration.FloodgateHook;
import com.skyzzz.guardian.core.integration.MetricsHook;
import com.skyzzz.guardian.core.integration.PlaceholderHook;
import com.skyzzz.guardian.core.packet.GuardianPacketListener;
import com.skyzzz.guardian.core.packet.PacketEventsHook;
import com.skyzzz.guardian.core.player.GuardianProfileManager;
import com.skyzzz.guardian.core.player.ProfileListener;
import com.skyzzz.guardian.core.punish.PunishmentManager;
import com.skyzzz.guardian.core.storage.MemoryViolationStore;
import com.skyzzz.guardian.core.storage.SqlViolationStore;
import com.skyzzz.guardian.core.util.Banner;
import com.skyzzz.guardian.core.util.Schedulers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class GuardianPlugin extends JavaPlugin implements GuardianAPI.Guardian {

    private GuardianConfig guardianConfig;
    private Messages messages;
    private GuardianProfileManager profileManager;
    private CheckRegistryImpl checkRegistry;
    private ViolationStore violationStore;
    private PunishmentManager punishmentManager;
    private AlertManager alertManager;
    private PacketEventsHook packetHook;
    private Schedulers schedulers;
    private FloodgateHook floodgateHook;

    @Override
    public void onEnable() {
        long startNanos = System.nanoTime();
        this.schedulers = new Schedulers(this);

        Banner.print(getPluginMeta().getVersion());

        // 1. Config + messages -------------------------------------------------
        this.guardianConfig = new GuardianConfig(this);
        this.guardianConfig.load();
        Banner.step("Configuration loaded", "config.yml");

        this.messages = new Messages(this);
        this.messages.load();
        Banner.step("Messages loaded", "messages.yml");

        // 2. Storage -----------------------------------------------------------
        this.violationStore = createStore();
        try {
            violationStore.init();
            Banner.step("Storage initialised", violationStore.getClass().getSimpleName());
        } catch (Exception exception) {
            Banner.warn("Storage init failed — falling back to memory-only");
            getLogger().log(Level.WARNING, "Violation store init failed", exception);
            this.violationStore = new MemoryViolationStore();
            try {
                violationStore.init();
                Banner.step("Storage initialised", "MemoryViolationStore (fallback)");
            } catch (Exception ignored) {
                // Memory store cannot fail to init.
            }
        }

        // 3. Player profiles ---------------------------------------------------
        this.floodgateHook = new FloodgateHook(this);
        this.profileManager = new GuardianProfileManager(this, guardianConfig, floodgateHook);
        Banner.step("Player profiles ready",
                floodgateHook.available() ? "Bedrock support enabled" : "Java only");

        // 4. Check registry ----------------------------------------------------
        this.checkRegistry = new CheckRegistryImpl(this, guardianConfig);
        int total = 0;
        for (Check check : CheckBootstrap.create()) {
            checkRegistry.register(check);
            total++;
        }
        checkRegistry.reloadAll();
        long enabled = checkRegistry.all().stream().filter(Check::isEnabled).count();
        Banner.step("Checks registered", enabled + " / " + total + " enabled");

        // 5. Punishment + alerts ----------------------------------------------
        this.punishmentManager = new PunishmentManager(this, guardianConfig, messages);
        this.alertManager = new AlertManager(this, guardianConfig, messages);
        this.punishmentManager.setAlertManager(alertManager);
        Banner.step("Punishment & alert systems ready");

        // 6. Listeners ---------------------------------------------------------
        Bukkit.getPluginManager().registerEvents(
                new ProfileListener(this, profileManager), this);
        Banner.step("Event listeners registered");

        // 7. PacketEvents ------------------------------------------------------
        this.packetHook = new PacketEventsHook(this);
        if (!packetHook.start(new GuardianPacketListener(this, profileManager, checkRegistry))) {
            Banner.fail("PacketEvents is missing or failed to hook");
            Banner.fail("Guardian cannot run without it — disabling");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Banner.step("PacketEvents hooked", "packet inspection active");

        // 8. Commands ----------------------------------------------------------
        GuardianCommand command = new GuardianCommand(this);
        if (getCommand("guardian") != null) {
            getCommand("guardian").setExecutor(command);
            getCommand("guardian").setTabCompleter(command);
        }
        Banner.step("Commands registered", "/guardian");

        // 9. Integrations ------------------------------------------------------
        StringBuilder integrations = new StringBuilder();
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            integrations.append("Vault ");
        }
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            integrations.append("LuckPerms ");
        }
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            integrations.append("PlaceholderAPI ");
        }
        if (floodgateHook.available()) {
            integrations.append("Floodgate ");
        }
        PlaceholderHook.register(this, profileManager, checkRegistry);
        new MetricsHook(this).start();
        Banner.step("Integrations loaded",
                integrations.length() == 0 ? "none" : integrations.toString().trim());

        // 10. API --------------------------------------------------------------
        GuardianAPI.register(this);

        // 11. Tick task --------------------------------------------------------
        schedulers.runSyncRepeating(this::tickProfiles, 1L, 1L);
        Banner.step("Tick task started");

        // ─── done ─────────────────────────────────────────────────────────
        long elapsed = (System.nanoTime() - startNanos) / 1_000_000L;
        Banner.blank();
        Banner.rule();
        Banner.blank();
        Banner.ready("Guardian is online  ·  enabled in " + elapsed + "ms");
        Banner.blank();
    }

    @Override
    public void onDisable() {
        Banner.blank();
        Banner.step("Shutting down Guardian");

        GuardianAPI.unregister();

        if (packetHook != null) {
            packetHook.stop();
            Banner.step("PacketEvents unhooked");
        }
        if (alertManager != null) {
            alertManager.shutdown();
            Banner.step("Alert system shut down");
        }
        if (violationStore != null) {
            violationStore.flush();
            violationStore.close();
            Banner.step("Storage flushed and closed");
        }
        if (profileManager != null) {
            profileManager.online().forEach(profile -> checkRegistry.dispatchQuit(profile));
        }
        Banner.ready("Guardian disabled");
        Banner.blank();
    }

    private void tickProfiles() {
        if (profileManager == null || checkRegistry == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            var profile = profileManager.get(player.getUniqueId());
            if (profile == null) {
                continue;
            }
            profile.setAttribute("server-tps", currentTps());
            profile.setPing(player.getPing());
            profile.setAttribute("health", player.getHealth());
            checkRegistry.dispatchTick(profile);
            profile.advanceTick();
        }
    }

    private double currentTps() {
        try {
            return Bukkit.getTPS()[0];
        } catch (Throwable ignored) {
            return 20.0D;
        }
    }

    private ViolationStore createStore() {
        String type = guardianConfig.getString("storage.type", "MEMORY");
        return switch (type.toUpperCase()) {
            case "SQLITE", "MYSQL", "MARIADB" -> new SqlViolationStore(this, guardianConfig);
            default -> new MemoryViolationStore();
        };
    }

    // ---- GuardianAPI.Guardian ------------------------------------------------

    @Override
    public ProfileManager profiles() {
        return profileManager;
    }

    @Override
    public CheckRegistry checks() {
        return checkRegistry;
    }

    @Override
    public ViolationStore violations() {
        return violationStore;
    }

    @Override
    public void reloadEverything() {
        Banner.blank();
        Banner.step("Reloading Guardian configuration");
        guardianConfig.load();
        messages.load();
        checkRegistry.reloadAll();
        alertManager.reload();
        punishmentManager.reload();
        floodgateHook.reload();
        // Platform flags are resolved at join; refresh online players so a reload
        // picks up Floodgate/Geyser installs without requiring rejoin.
        for (Player online : Bukkit.getOnlinePlayers()) {
            profileManager.refreshPlatform(online);
        }
        Banner.ready("Reload complete");
        Banner.blank();
    }

    @Override
    public void runSync(Runnable task) {
        schedulers.runSync(task);
    }

    @Override
    public void runAsync(Runnable task) {
        schedulers.runAsync(task);
    }

    // ---- accessors -----------------------------------------------------------

    public GuardianConfig guardianConfig() {
        return guardianConfig;
    }

    public Messages messages() {
        return messages;
    }

    public PunishmentManager punishmentManager() {
        return punishmentManager;
    }

    public AlertManager alertManager() {
        return alertManager;
    }

    public GuardianProfileManager profileManager() {
        return profileManager;
    }

    public CheckRegistryImpl checkRegistry() {
        return checkRegistry;
    }

    public Schedulers schedulers() {
        return schedulers;
    }

    public FloodgateHook floodgateHook() {
        return floodgateHook;
    }
}