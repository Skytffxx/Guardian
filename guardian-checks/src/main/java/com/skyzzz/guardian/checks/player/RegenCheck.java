package com.skyzzz.guardian.checks.player;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.player.PlayerProfile;
import com.skyzzz.guardian.api.util.RollingWindow;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Health regeneration anomaly.
 *
 * Vanilla regen: 1 HP every 4 seconds (80 ticks) with full hunger, halved per
 * Regeneration level (level I = 50 ticks, level II = 25, etc). Health that climbs
 * faster than the effect allows over a sustained window is suspicious.
 *
 * A single fast heal is exempt — custom plugins, admins with /heal, world change
 * damage absorption. Requires the rate to stay elevated across a full rolling window.
 */
public final class RegenCheck extends AbstractCheck {

    private static final class State {
        double lastHealth = -1.0D;
        long lastHealTick = Long.MIN_VALUE;
        final RollingWindow intervalWindow;

        State(int windowSize) {
            this.intervalWindow = new RollingWindow(windowSize);
        }
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public RegenCheck() {
        super("regen", CheckCategory.PLAYER);
    }

    @Override
    public void onTick(PlayerProfile profile) {
        State state = states.computeIfAbsent(profile.uuid(),
                k -> new State(i("window-size", 8)));

        Object healthAttr = profile.attribute("health");
        if (!(healthAttr instanceof Number healthNum)) {
            return;
        }
        double health = healthNum.doubleValue();

        if (Boolean.TRUE.equals(profile.attribute("creative"))
                || Boolean.TRUE.equals(profile.attribute("spectator"))) {
            state.lastHealth = health;
            return;
        }

        if (state.lastHealth < 0.0D) {
            state.lastHealth = health;
            return;
        }

        if (health > state.lastHealth) {
            long currentTick = profile.tick();
            if (state.lastHealTick != Long.MIN_VALUE) {
                long gap = currentTick - state.lastHealTick;
                state.intervalWindow.add(gap);
            }
            state.lastHealTick = currentTick;
        }
        state.lastHealth = health;

        if (!state.intervalWindow.isFull()) {
            return;
        }

        // Vanilla baseline: regen I heals 1 HP every 50 ticks minimum.
        // Anything sustaining below that cadence for a full window is suspicious.
        double mean = state.intervalWindow.mean();
        double minimum = scaled(profile, "minimum-heal-interval-ticks", 20.0D);

        if (mean < minimum) {
            flag(profile, 1.5D,
                    "regen cadence %.1f ticks/HP (minimum %.1f) over %d heals",
                    mean, minimum, state.intervalWindow.size());
            state.intervalWindow.clear();
        }
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}