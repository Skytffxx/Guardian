package com.skyzzz.guardian.core.check;

import com.skyzzz.guardian.api.check.Check;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.check.CheckRegistry;
import com.skyzzz.guardian.api.check.CheckSettings;
import com.skyzzz.guardian.api.data.DamageData;
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

public final class CheckRegistryImpl implements CheckRegistry {

    private final GuardianPlugin plugin;
    private final GuardianConfig config;

    private final Map<String, Check> byName = new ConcurrentHashMap<>();
    private final Map<CheckCategory, List<Check>> byCategory = new ConcurrentHashMap<>();

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

    @Override
    public void dispatchPacketReceive(PlayerProfile profile, PacketData data) {
        dispatchCategory(CheckCategory.PACKET, c -> c.onPacketReceive(profile, data));
        dispatchCategory(CheckCategory.COMBAT, c -> c.onPacketReceive(profile, data));
        dispatchCategory(CheckCategory.MOVEMENT, c -> c.onPacketReceive(profile, data));
        dispatchCategory(CheckCategory.PLAYER, c -> c.onPacketReceive(profile, data));
        dispatchCategory(CheckCategory.WORLD, c -> c.onPacketReceive(profile, data));
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
        dispatchCategory(CheckCategory.MOVEMENT, c -> c.onMove(profile, data));
        dispatchCategory(CheckCategory.COMBAT, c -> c.onMove(profile, data));
    }

    @Override
    public void dispatchAttack(PlayerProfile profile, AttackData data) {
        dispatchCategory(CheckCategory.COMBAT, c -> c.onAttack(profile, data));
    }

    @Override
    public void dispatchBlockPlace(PlayerProfile profile, BlockPlaceData data) {
        dispatchCategory(CheckCategory.WORLD, c -> c.onBlockPlace(profile, data));
    }

    @Override
    public void dispatchBlockBreak(PlayerProfile profile, BlockBreakData data) {
        dispatchCategory(CheckCategory.WORLD, c -> c.onBlockBreak(profile, data));
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

    private void dispatchCategory(CheckCategory category, java.util.function.Consumer<Check> action) {
        for (Check check : byCategory.get(category)) {
            if (check.isEnabled()) {
                safe(() -> action.accept(check), check);
            }
        }
    }

    private void safe(Runnable action, Check check) {
        try {
            action.run();
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.SEVERE,
                    "Check '" + check.name() + "' threw and has been disabled", throwable);
            check.setEnabled(false);
        }
    }

    @Override
    public void dispatchDamage(PlayerProfile profile, DamageData data) {
        dispatchCategory(CheckCategory.COMBAT, c -> c.onDamage(profile, data));
    }
}