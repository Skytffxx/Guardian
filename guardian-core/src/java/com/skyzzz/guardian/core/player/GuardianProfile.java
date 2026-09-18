package com.skyzzz.guardian.core.player;

import com.skyzzz.guardian.api.Platform;
import com.skyzzz.guardian.api.check.Check;
import com.skyzzz.guardian.api.player.ClickHistory;
import com.skyzzz.guardian.api.player.PlayerProfile;
import com.skyzzz.guardian.api.player.PositionHistory;
import com.skyzzz.guardian.api.violation.ViolationLevel;
import com.skyzzz.guardian.core.GuardianPlugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuardianProfile implements PlayerProfile {

    private final GuardianPlugin plugin;
    private final UUID uuid;
    private final String name;

    private volatile Platform platform = Platform.JAVA;
    private volatile double thresholdScale = 1.0D;
    private volatile int ping;
    private volatile int clientVersion;

    private long tick;

    private final PositionHistory positions;
    private final ClickHistory clicks;
    private final Map<String, ViolationLevel> violations = new ConcurrentHashMap<>();
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final Set<String> exemptions = ConcurrentHashMap.newKeySet();
    private final Set<String> debugEnabled = ConcurrentHashMap.newKeySet();

    public GuardianProfile(GuardianPlugin plugin, UUID uuid, String name,
                           int positionHistorySize, int clickHistorySize) {
        this.plugin = plugin;
        this.uuid = uuid;
        this.name = name;
        this.positions = new PositionHistory(positionHistorySize);
        this.clicks = new ClickHistory(clickHistorySize);
    }

    @Override
    public UUID uuid() {
        return uuid;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Platform platform() {
        return platform;
    }

    @Override
    public void setPlatform(Platform platform) {
        this.platform = platform;
    }

    @Override
    public double thresholdScale() {
        return isBedrock() ? thresholdScale : 1.0D;
    }

    public void setThresholdScale(double thresholdScale) {
        this.thresholdScale = Math.max(1.0D, thresholdScale);
    }

    @Override
    public int ping() {
        return ping;
    }

    @Override
    public void setPing(int ping) {
        this.ping = Math.max(0, ping);
    }

    @Override
    public int clientVersion() {
        return clientVersion;
    }

    @Override
    public void setClientVersion(int clientVersion) {
        this.clientVersion = clientVersion;
    }

    @Override
    public long tick() {
        return tick;
    }

    @Override
    public void advanceTick() {
        tick++;
    }

    @Override
    public PositionHistory positions() {
        return positions;
    }

    @Override
    public ClickHistory clicks() {
        return clicks;
    }

    @Override
    public ViolationLevel violations(String checkName) {
        return violations.computeIfAbsent(checkName.toLowerCase(), key -> new ViolationLevel(
                plugin.guardianConfig().getDouble(
                        "violations.decay-per-second", 0.35D)));
    }

    @Override
    public Map<String, Double> violationSnapshot() {
        Map<String, Double> snapshot = new HashMap<>();
        violations.forEach((key, level) -> snapshot.put(key, level.value()));
        return Collections.unmodifiableMap(snapshot);
    }

    @Override
    public double flag(Check check, double weight, String debug, Object... args) {
        if (isExempt(check)) {
            return violations(check.name()).value();
        }
        String formatted = args.length == 0 ? debug : String.format(debug, args);
        double newLevel = violations(check.name()).add(weight);

        if (debugEnabled(check.name())) {
            sendDebug(String.format("[%s] +%.2f -> %.2f | %s",
                    check.name(), weight, newLevel, formatted));
        }

        plugin.punishmentManager().handleFlag(this, check, newLevel, formatted);
        return newLevel;
    }

    @Override
    public void reward(Check check, double amount) {
        ViolationLevel level = violations.get(check.name().toLowerCase());
        if (level != null) {
            level.subtract(amount);
        }
    }

    @Override
    public boolean isExempt(Check check) {
        return exemptions.contains("all")
                || exemptions.contains(check.category().key())
                || exemptions.contains(check.name().toLowerCase());
    }

    @Override
    public void setExempt(String reason, boolean exempt) {
        if (exempt) {
            exemptions.add(reason.toLowerCase());
        } else {
            exemptions.remove(reason.toLowerCase());
        }
    }

    @Override
    public boolean debugEnabled(String checkName) {
        return debugEnabled.contains(checkName.toLowerCase()) || debugEnabled.contains("all");
    }

    @Override
    public void setDebug(String checkName, boolean enabled) {
        if (enabled) {
            debugEnabled.add(checkName.toLowerCase());
        } else {
            debugEnabled.remove(checkName.toLowerCase());
        }
    }

    @Override
    public void sendDebug(String message) {
        var player = plugin.getServer().getPlayer(uuid);
        if (player != null) {
            player.sendMessage(plugin.messages().format("debug-format", message));
        }
    }

    @Override
    public Object attribute(String key) {
        return attributes.get(key);
    }

    @Override
    public void setAttribute(String key, Object value) {
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }

    @Override
    public void reset() {
        positions.clear();
        clicks.clear();
        violations.values().forEach(ViolationLevel::reset);
        attributes.clear();
    }
}