package com.skyzzz.guardian.core.player;

import com.skyzzz.guardian.api.Platform;
import com.skyzzz.guardian.api.player.PlayerProfile;
import com.skyzzz.guardian.api.player.ProfileManager;
import com.skyzzz.guardian.api.violation.ViolationLevel;
import com.skyzzz.guardian.core.GuardianPlugin;
import com.skyzzz.guardian.core.config.GuardianConfig;
import com.skyzzz.guardian.core.integration.FloodgateHook;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuardianProfileManager implements ProfileManager {

    private final GuardianPlugin plugin;
    private final GuardianConfig config;
    private final FloodgateHook floodgate;
    private final Map<UUID, GuardianProfile> profiles = new ConcurrentHashMap<>();

    public GuardianProfileManager(GuardianPlugin plugin, GuardianConfig config,
                                  FloodgateHook floodgate) {
        this.plugin = plugin;
        this.config = config;
        this.floodgate = floodgate;
    }

    /** Creates the profile if absent. Safe to call from any thread. */
    public GuardianProfile getOrCreate(Player player) {
        return profiles.computeIfAbsent(player.getUniqueId(), uuid -> {
            GuardianProfile profile = new GuardianProfile(plugin, uuid, player.getName(),
                    config.getInt("performance.position-history-size", 40),
                    config.getInt("performance.click-history-size", 40));

            boolean bedrock = floodgate.isBedrockPlayer(uuid);
            profile.setPlatform(bedrock ? Platform.BEDROCK : Platform.JAVA);
            profile.setThresholdScale(bedrock
                    ? config.getDouble("false-positives.bedrock-threshold-multiplier", 1.35D)
                    : 1.0D);
            profile.setPing(player.getPing());
            return profile;
        });
    }

    @Override
    public PlayerProfile get(UUID uuid) {
        return profiles.get(uuid);
    }

    @Override
    public Collection<PlayerProfile> online() {
        return Collections.unmodifiableCollection(profiles.values());
    }

    @Override
    public void remove(UUID uuid) {
        profiles.remove(uuid);
    }

    @Override
    public void tickAll() {
        // Retained for API completeness; core's repeating task drives ticks so that
        // TPS/ping refresh happens in the same pass.
    }

    @Override
    public double totalViolations(UUID uuid) {
        GuardianProfile profile = profiles.get(uuid);
        if (profile == null) {
            return 0.0D;
        }
        double total = 0.0D;
        for (Double value : profile.violationSnapshot().values()) {
            total += value;
        }
        return total;
    }

    public Collection<GuardianProfile> rawProfiles() {
        return Collections.unmodifiableCollection(profiles.values());
    }
}