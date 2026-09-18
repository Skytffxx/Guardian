package com.skyzzz.guardian.core.check;

import com.skyzzz.guardian.api.check.Check;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.check.CheckRegistry;
import com.skyzzz.guardian.api.check.CheckSettings;
import com.skyzzz.guardian.api.data.AttackData;
import com.skyzzz.guardian.api.data.BlockBreakData;
import com.skyzzz.guardian.api.data.BlockPlaceData;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.data.PacketData;
import com.skyzzz.guardian.api.player.PlayerProfile;
import com.skyzzz.guardian.core.GuardianPlugin;
import com.skyzzz.guardian.core.config.BukkitCheckSettings;
import com.skyzzz.guardian.core.config.GuardianConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Holds every check. Dispatch is a plain loop over a pre-sorted list per category,
 * with a fast bail-out when a category has no enabled checks.
 */
public final class CheckRegistryImpl implements CheckRegistry {

    private final GuardianPlugin plugin;
    private final GuardianConfig config;

    private final Map<String, Check> byName = new ConcurrentHashMap<>();
    private final Map<CheckCategory, List<Check>> byCategory =
            new ConcurrentHashMap<>();

    public CheckRegistryImpl(GuardianPlugin plugin, GuardianConfig config) {
        this.plugin = plugin;
        this.config = config;
        for (CheckCategory category : CheckCategory.values()) {
            byCategory.put(category, Collections.synchronizedList(new ArrayList<>()));
        }
    }

    @Override
    public void register(Check check) {
        Check previous = byName.put(check.name().toLowerCase(), check);
        if (previous != null) {
            byCategory.get(previous.category()).remove(previous);
        }
        byCategory.get(check.category()).add(check);
    }

    @Override
    public void unregister(String name) {
        Check removed = byName.remove(name.toLowerCase());
        if (removed != null) {
            byCategory.get(removed.category()).remove(removed);
        }
    }

    @Override
    public Optional<Check> get(String name) {
        return Optional.ofNullable(byName.get(name.toLowerCase()));
    }

    @Override
    public Collection<Check> all() {
        return Collections.unmodifiableCollection(byName.values());
    }

    @Override
    public List<Check> byCategory(CheckCategory category) {
        return Collections.unmodifiableList(byCategory.get(category));
    }

    @Override
    public boolean setEnabled(String name, boolean enabled) {
        Check check = byName.get(name.toLowerCase());
        if (check == null) {
            return false;
        }
        check.setEnabled(enabled);
        config.set("checks." + check.category().key() + "." + check.name() + ".enabled", enabled);
        return true;
    }

    @Override
    public void reloadAll() {
        for (Check check : byName.values()) {
            try {
                CheckSettings settings = new BukkitCheckSettings(
                        config.section("checks." + check.category().key() + "." + check.name()),
                        config);
                check.bind(settings);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to bind settings for check " + check.name()
                                + "; disabling it for safety", exception);
                check.setEnabled(false);
            }
        }
    }

    // ---- dispatch --------------------------------------------------------

    @Override
    public void dispatchPacketReceive(PlayerProfile profile, PacketData data) {
        for (Check check : byCategory.get(CheckCategory.PACKET)) {
            if (check.isEnabled()) {
                safe(() -> check.onPacketReceive(profile, data), check);
            }
        }
        for (Check check : byCategory.get(CheckCategory.COMBAT)) {
            if (check.isEnabled()) {
                safe(() -> check.onPacketReceive(profile, data), check);
            }
        }
        for (Check check : byCategory.get(CheckCategory.MOVEMENT)) {
            if (check.isEnabled()) {
                safe(() -> check.onPacketReceive(profile, data), check);
            }
        }
        for (Check check : byCategory.get(CheckCategory.PLAYER)) {
            if (check.isEnabled()) {
                safe(() -> check.onPacketReceive(profile, data), check);
            }
        }
        for (Check check : byCategory.get(CheckCategory.WORLD)) {
            if (check.isEnabled()) {
                safe(() -> check.onPacketReceive(profile, data), check);
            }
        }
    }

    @Override
    public void dispatchPacketSend(PlayerProfile profile, PacketData data) {
        for (List<Check> checks : byCategory.values()) {
            for (Check check : checks) {
                if (check.isEnabled()) {
                    safe(() -> check.onPacketSend(profile, data), check);
                }
            }
        }
    }

    @Override
    public void dispatchMove(PlayerProfile profile, MoveData data) {
        for (Check check : byCategory.get(CheckCategory.MOVEMENT)) {
            if (check.isEnabled()) {
                safe(() -> check.onMove(profile, data), check);
            }
        }
        for (Check check : byCategory.get(CheckCategory.COMBAT)) {
            if (check.isEnabled()) {
                safe(() -> check.onMove(profile, data), check);
            }
        }
    }

    @Override
    public void dispatchAttack(PlayerProfile profile, AttackData data) {
        for (Check check : byCategory.get(CheckCategory.COMBAT)) {
            if (check.isEnabled()) {
                safe(() -> check.onAttack(profile, data), check);
            }
        }
    }

    @Override
    public void dispatchBlockPlace(PlayerProfile profile, BlockPlaceData data) {
        for (Check check : byCategory.get(CheckCategory.WORLD)) {
            if (check.isEnabled()) {
                safe(() -> check.onBlockPlace(profile, data), check);
            }
        }
    }

    @Override
    public void dispatchBlockBreak(PlayerProfile profile, BlockBreakData data) {
        for (Check check : byCategory.get(CheckCategory.WORLD)) {
            if (check.isEnabled()) {
                safe(() -> check.onBlockBreak(profile, data), check);
            }
        }
    }

    @Override
    public void dispatchTick(PlayerProfile profile) {
        for (List<Check> checks : byCategory.values()) {
            for (Check check : checks) {
                if (check.isEnabled()) {
                    safe(() -> check.onTick(profile), check);
                }
            }
        }
    }

    @Override
    public void dispatchJoin(PlayerProfile profile) {
        for (List<Check> checks : byCategory.values()) {
            for (Check check : checks) {
                if (check.isEnabled()) {
                    safe(() -> check.onJoin(profile), check);
                }
            }
        }
    }

    @Override
    public void dispatchQuit(PlayerProfile profile) {
        for (List<Check> checks : byCategory.values()) {
            for (Check check : checks) {
                safe(() -> check.onQuit(profile), check);
            }
        }
    }

    /**
     * A throwing check must never take down packet handling for everyone.
     * We log once per check per session and disable it.
     */
    private void safe(Runnable action, Check check) {
        try {
            action.run();
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.SEVERE,
                    "Check '" + check.name() + "' threw and has been disabled", throwable);
            check.setEnabled(false);
        }
    }
}