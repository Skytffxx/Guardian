package com.skyzzz.guardian.core.check;

import com.skyzzz.guardian.api.check.Check;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.check.CheckRegistry;
import com.skyzzz.guardian.api.check.CheckSettings;
import com.skyzzz.guardian.api.data.AttackData;
import com.skyzzz.guardian.api.data.BlockBreakData;
import com.skyzzz.guardian.api.data.BlockPlaceData;
import com.skyzzz.guardian.api.data.DamageData;
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

public final class CheckRegistryImpl implements CheckRegistry {

    private final GuardianPlugin plugin;
    private final GuardianConfig config;

    private final Map<String, Check> byName = new ConcurrentHashMap<>();
    private final Map<CheckCategory, List<Check>> byCategory = new ConcurrentHashMap<>();
    // Pre-filtered snapshots: rebuilding the enabled list per packet was the hottest
    // allocation in the pipeline.
    private volatile List<Check> moveChecks = List.of();
    private volatile List<Check> extraChecks = List.of();
    private volatile List<Check> tickChecks = List.of();

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
        refreshCaches();
    }

    @Override
    public void unregister(String name) {
        Check check = byName.remove(name.toLowerCase());
        if (check == null) {
            return;
        }
        byCategory.get(check.category()).remove(check);
        refreshCaches();
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
        refreshCaches();
        return true;
    }

    @Override
    public void reloadAll() {
        for (Check check : byName.values()) {
            String path = "checks." + check.category().key() + "." + check.name();
            try {
                CheckSettings settings = new BukkitCheckSettings(config.section(path), config);
                check.bind(settings);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to bind settings for check "
                        + check.name() + "; disabling it for safety", exception);
                check.setEnabled(false);
            }
        }
        refreshCaches();
    }

    @Override
    public void dispatchPacketReceive(PlayerProfile profile, PacketData data) {
        for (Check check : moveChecks) {
            safe(() -> check.onPacketReceive(profile, data), check);
        }
        for (Check check : extraChecks) {
            safe(() -> check.onPacketReceive(profile, data), check);
        }
    }

    @Override
    public void dispatchPacketSend(PlayerProfile profile, PacketData data) {
        for (Check check : tickChecks) {
            if (check.isEnabled()) {
                safe(() -> check.onPacketSend(profile, data), check);
            }
        }
    }

    @Override
    public void dispatchMove(PlayerProfile profile, MoveData data) {
        for (Check check : moveChecks) {
            safe(() -> check.onMove(profile, data), check);
        }
    }

    @Override
    public void dispatchAttack(PlayerProfile profile, AttackData data) {
        for (Check check : moveChecks) {
            if (check.category() == CheckCategory.COMBAT) {
                safe(() -> check.onAttack(profile, data), check);
            }
        }
    }

    @Override
    public void dispatchBlockPlace(PlayerProfile profile, BlockPlaceData data) {
        for (Check check : extraChecks) {
            if (check.category() == CheckCategory.WORLD) {
                safe(() -> check.onBlockPlace(profile, data), check);
            }
        }
    }

    @Override
    public void dispatchBlockBreak(PlayerProfile profile, BlockBreakData data) {
        for (Check check : extraChecks) {
            if (check.category() == CheckCategory.WORLD) {
                safe(() -> check.onBlockBreak(profile, data), check);
            }
        }
    }

    @Override
    public void dispatchTick(PlayerProfile profile) {
        for (Check check : tickChecks) {
            if (check.isEnabled()) {
                safe(() -> check.onTick(profile), check);
            }
        }
    }

    @Override
    public void dispatchJoin(PlayerProfile profile) {
        for (Check check : tickChecks) {
            if (check.isEnabled()) {
                safe(() -> check.onJoin(profile), check);
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

    @Override
    public void dispatchDamage(PlayerProfile profile, DamageData data) {
        for (Check check : moveChecks) {
            if (check.category() == CheckCategory.COMBAT) {
                safe(() -> check.onDamage(profile, data), check);
            }
        }
    }

    /** Called whenever the enabled set changes: register, unregister, reload, toggle, auto-disable. */
    private void refreshCaches() {
        List<Check> move = new ArrayList<>();
        List<Check> extra = new ArrayList<>();
        List<Check> rest = new ArrayList<>();
        for (Check check : byName.values()) {
            if (!check.isEnabled()) {
                continue;
            }
            switch (check.category()) {
                case PACKET, MOVEMENT, COMBAT -> move.add(check);
                case PLAYER, WORLD -> extra.add(check);
            }
            rest.add(check);
        }
        moveChecks = List.copyOf(move);
        extraChecks = List.copyOf(extra);
        tickChecks = List.copyOf(rest);
    }

    private void safe(Runnable action, Check check) {
        try {
            action.run();
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.SEVERE,
                    "Check '" + check.name() + "' threw and has been disabled", throwable);
            check.setEnabled(false);
            refreshCaches();
        }
    }
}