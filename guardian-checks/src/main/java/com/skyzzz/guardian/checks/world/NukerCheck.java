package com.skyzzz.guardian.checks.world;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.BlockBreakData;
import com.skyzzz.guardian.api.player.PlayerProfile;
import com.skyzzz.guardian.api.util.RollingWindow;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nuker: multiple block breaks in a single tick, or an implausible break rate across
 * a window. Instant-break blocks (tall grass, flowers) are excluded because they
 * legitimately break in one tick each.
 */
public final class NukerCheck extends AbstractCheck {

    private static final class State {
        int breaksThisTick;
        int lastTick = Integer.MIN_VALUE;
        final RollingWindow perTickWindow;

        State(int window) {
            this.perTickWindow = new RollingWindow(window);
        }
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public NukerCheck() {
        super("nuker", CheckCategory.WORLD);
    }

    @Override
    public void onBlockBreak(PlayerProfile profile, BlockBreakData breakData) {
        State state = states.computeIfAbsent(profile.uuid(),
                key -> new State(i("window-size", 20)));

        int currentTick = (int) profile.tick();
        if (currentTick != state.lastTick) {
            state.perTickWindow.add(state.breaksThisTick);
            state.lastTick = currentTick;
            state.breaksThisTick = 0;
        }
        state.breaksThisTick++;

        if (state.breaksThisTick > i("max-breaks-per-tick", 1)) {
            flag(profile, 2.5D, "breaks/tick=%d material=%s at %d,%d,%d",
                    state.breaksThisTick, breakData.material(),
                    breakData.blockX(), breakData.blockY(), breakData.blockZ());
            state.breaksThisTick = 0;
            return;
        }

        if (!state.perTickWindow.isFull()) {
            return;
        }

        double mean = state.perTickWindow.mean();
        if (mean > d("max-mean-breaks-per-tick", 0.85D)) {
            flag(profile, 1.5D, "mean breaks/tick=%.3f over %d ticks",
                    mean, state.perTickWindow.size());
            state.perTickWindow.clear();
        }
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}