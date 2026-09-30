package com.skyzzz.guardian.checks.player;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.player.PlayerProfile;
import com.skyzzz.guardian.api.util.RollingWindow;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Regen cadence. Vanilla heals 1 HP every 80 ticks at full hunger, halved per
 * Regeneration level, so a window that sustains better than that is suspicious. One
 * fast heal is exempt; /heal and custom plugins do that legitimately.
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
            if (state.lastHealTick != Long.MIN_VALUE) {
                state.intervalWindow.add(profile.tick() - state.lastHealTick);
            }
            state.lastHealTick = profile.tick();
        }
        state.lastHealth = health;

        if (!state.intervalWindow.isFull()) {
            return;
        }

        // Regen I heals 1 HP every 50 ticks at best.
        double avg = state.intervalWindow.mean();
        double minimum = scaled(profile, "minimum-heal-interval-ticks", 20.0D);

        if (avg < minimum) {
            flag(profile, 1.5D,
                    "regen cadence %.1f ticks/HP (minimum %.1f) over %d heals",
                    avg, minimum, state.intervalWindow.size());
            state.intervalWindow.clear();
        }
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}