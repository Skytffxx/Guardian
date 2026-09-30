package com.skyzzz.guardian.core.punish;

import com.skyzzz.guardian.api.check.Check;
import com.skyzzz.guardian.api.player.PlayerProfile;
import com.skyzzz.guardian.core.GuardianPlugin;
import com.skyzzz.guardian.core.alert.AlertManager;
import com.skyzzz.guardian.core.config.GuardianConfig;
import com.skyzzz.guardian.core.config.Messages;
import com.skyzzz.guardian.api.violation.ViolationRecord;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Turns VL thresholds into configurable console commands, so LiteBans, AdvancedBan,
 * vanilla or anything else works without a Guardian-specific integration. Tier
 * cooldowns keep a lag spike from firing the same tier repeatedly.
 */
public final class PunishmentManager {

    private static final long DEFAULT_COOLDOWN_MILLIS = 30_000L;

    private final GuardianPlugin plugin;
    private final GuardianConfig config;
    private final Messages messages;

    private final Map<String, Long> lastPunish = new ConcurrentHashMap<>();
    private final Map<String, Long> alertThrottle = new ConcurrentHashMap<>();
    private final AtomicLong punishCount = new AtomicLong();

    private AlertManager alertManager;

    public PunishmentManager(GuardianPlugin plugin, GuardianConfig config, Messages messages) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
    }

    public void setAlertManager(AlertManager alertManager) {
        this.alertManager = alertManager;
    }

    public void reload() {
        lastPunish.clear();
        alertThrottle.clear();
    }

    public void handleFlag(PlayerProfile profile, Check check, double newLevel, String debug) {
        // One alert per player+check per throttle window; a burst must not spam.
        long now = System.currentTimeMillis();
        long throttleMs = config.getInt("alerts.throttle-ms", 1500);
        String alertKey = profile.uuid() + ":" + check.name();
        Long lastAlert = alertThrottle.get(alertKey);
        if (lastAlert == null || now - lastAlert >= throttleMs) {
            alertThrottle.put(alertKey, now);
            if (alertManager != null) {
                alertManager.alert(profile, check, newLevel, debug);
            }
            plugin.violations().record(new ViolationRecord(
                    0L, profile.uuid(), profile.name(), check.name(),
                    check.category().key(), newLevel, debug, now));
        }

        evaluateTiers(profile, check, newLevel);
    }

    private void evaluateTiers(PlayerProfile profile, Check check, double level) {
        String checkPath = "punishments.checks." + check.name();
        String categoryPath = "punishments.categories." + check.category().key();
        String globalPath = "punishments.global";

        applyTiers(profile, check.name(), level, checkPath);
        applyTiers(profile, check.category().key(), level, categoryPath);
        applyTiers(profile, "global", level, globalPath);
    }

    private void applyTiers(PlayerProfile profile, String source, double level, String path) {
        List<Map<?, ?>> tiers = config.raw().getMapList(path + ".tiers");

        for (Map<?, ?> tier : tiers) {
            if (!(tier.get("threshold") instanceof Number thresholdValue)) {
                continue;
            }
            double threshold = thresholdValue.doubleValue();
            if (level < threshold) {
                continue;
            }

            long cooldown = tier.get("cooldown-seconds") instanceof Number seconds
                    ? seconds.longValue() * 1000L
                    : DEFAULT_COOLDOWN_MILLIS;

            String key = profile.uuid() + ":" + source + ":" + threshold;
            long now = System.currentTimeMillis();
            Long last = lastPunish.get(key);
            if (last != null && now - last < cooldown) {
                continue;
            }
            lastPunish.put(key, now);

            if (!(tier.get("commands") instanceof List<?> commands)) {
                continue;
            }
            for (Object entry : commands) {
                if (!(entry instanceof String command) || command.isBlank()) {
                    continue;
                }
                String resolved = command
                        .replace("{player}", profile.name())
                        .replace("{uuid}", profile.uuid().toString())
                        .replace("{check}", source)
                        .replace("{vl}", String.format("%.2f", level));

                plugin.schedulers().runSync(() ->
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved));
                punishCount.incrementAndGet();
            }

            if (alertManager != null) {
                alertManager.punishment(profile, source, level, threshold);
            }
        }
    }

    public long punishmentCount() {
        return punishCount.get();
    }

    public void clear(UUID uuid) {
        lastPunish.keySet().removeIf(key -> key.startsWith(uuid.toString()));
        alertThrottle.keySet().removeIf(key -> key.startsWith(uuid.toString()));
    }
}