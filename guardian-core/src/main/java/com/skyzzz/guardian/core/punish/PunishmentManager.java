package com.skyzzz.guardian.core.punish;

import com.skyzzz.guardian.api.check.Check;
import com.skyzzz.guardian.api.player.PlayerProfile;
import com.skyzzz.guardian.core.GuardianPlugin;
import com.skyzzz.guardian.core.alert.AlertManager;
import com.skyzzz.guardian.core.config.GuardianConfig;
import com.skyzzz.guardian.core.config.Messages;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * VL thresholds → commands. Never hardcodes a punishment plugin: it fires
 * configurable commands, so LiteBans, AdvancedBan, vanilla ban, or anything else
 * works without a code change.
 *
 * Cooldowns prevent a lag-induced VL spike from firing the same tier five times.
 */
public final class PunishmentManager {

    private static final class Tier {
        final double threshold;
        final List<String> commands;
        final long cooldownMillis;

        Tier(double threshold, List<String> commands, long cooldownMillis) {
            this.threshold = threshold;
            this.commands = commands;
            this.cooldownMillis = cooldownMillis;
        }
    }

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
        // Alerts are throttled per player+check so a burst produces one message.
        long now = System.currentTimeMillis();
        long throttleMs = config.getInt("alerts.throttle-ms", 1500);
        String alertKey = profile.uuid() + ":" + check.name();
        Long lastAlert = alertThrottle.get(alertKey);
        if (lastAlert == null || now - lastAlert >= throttleMs) {
            alertThrottle.put(alertKey, now);
            if (alertManager != null) {
                alertManager.alert(profile, check, newLevel, debug);
            }
            plugin.violations().record(new com.skyzzz.guardian.api.violation.ViolationRecord(
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
        if (tiers.isEmpty()) {
            return;
        }

        for (Map<?, ?> rawTier : tiers) {
            Object thresholdObject = rawTier.get("threshold");
            if (!(thresholdObject instanceof Number thresholdNumber)) {
                continue;
            }
            double threshold = thresholdNumber.doubleValue();
            if (level < threshold) {
                continue;
            }

            long cooldown = rawTier.get("cooldown-seconds") instanceof Number cooldownNumber
                    ? cooldownNumber.longValue() * 1000L
                    : 30_000L;

            String key = profile.uuid() + ":" + source + ":" + threshold;
            long now = System.currentTimeMillis();
            Long last = lastPunish.get(key);
            if (last != null && now - last < cooldown) {
                continue;
            }
            lastPunish.put(key, now);

            Object commandsObject = rawTier.get("commands");
            if (!(commandsObject instanceof List<?> commandList)) {
                continue;
            }

            for (Object entry : commandList) {
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