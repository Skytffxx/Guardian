package com.skyzzz.guardian.checks.combat;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.player.PlayerProfile;
import com.skyzzz.guardian.api.util.MathUtil;
import com.skyzzz.guardian.api.util.RollingWindow;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rotation GCD analysis. A mouse yields deltas that are irrational multiples of the
 * sensitivity step, so their GCD collapses toward zero; a cheat deriving rotations
 * from a fixed step (or snapping to a target) leaves deltas sharing a large divisor.
 * A high GCD only counts once it persists across a full window.
 */
public final class AimCheck extends AbstractCheck {

    private static final class State {
        double lastYawDelta;
        double lastPitchDelta;
        double gcdYaw = 0.0D;
        double gcdPitch = 0.0D;
        final RollingWindow yawWindow;
        final RollingWindow pitchWindow;

        State(int window) {
            yawWindow = new RollingWindow(window);
            pitchWindow = new RollingWindow(window);
        }
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public AimCheck() {
        super("aim", CheckCategory.COMBAT);
    }

    @Override
    public void onMove(PlayerProfile profile, MoveData move) {
        if (!move.rotationChanged()) {
            return;
        }
        // Teleports and vehicle exits produce huge single-tick deltas.
        if (move.yawDeltaAbs() > d("ignore-above-yaw-delta", 90.0F)
                || move.pitchDeltaAbs() > d("ignore-above-pitch-delta", 60.0F)) {
            states.remove(profile.uuid());
            return;
        }

        State state = states.computeIfAbsent(profile.uuid(), key -> new State(i("window-size", 40)));
        double yawDelta = move.deltaYaw();
        double pitchDelta = move.deltaPitch();

        if (state.lastYawDelta != 0.0D && yawDelta != 0.0D) {
            state.gcdYaw = MathUtil.gcd(state.lastYawDelta, yawDelta);
            state.yawWindow.add(state.gcdYaw);
        }
        if (state.lastPitchDelta != 0.0D && pitchDelta != 0.0D) {
            state.gcdPitch = MathUtil.gcd(state.lastPitchDelta, pitchDelta);
            state.pitchWindow.add(state.gcdPitch);
        }

        state.lastYawDelta = yawDelta;
        state.lastPitchDelta = pitchDelta;

        if (!state.yawWindow.isFull() || !state.pitchWindow.isFull()) {
            return;
        }

        double minGcd = scaled(profile, "min-gcd", 0.009D);
        double required = d("required-above-ratio", 0.72D);
        double yawRatio = (double) state.yawWindow.countAbove(minGcd) / state.yawWindow.size();
        double pitchRatio = (double) state.pitchWindow.countAbove(minGcd) / state.pitchWindow.size();

        if (yawRatio < required || pitchRatio < required) {
            reward(profile, d("clean-reward", 0.2D));
            return;
        }

        flag(profile, 1.0D + (yawRatio + pitchRatio) / 2.0D,
                "gcdYaw=%.5f (%.0f%%) gcdPitch=%.5f (%.0f%%) window=%d",
                state.yawWindow.mean(), yawRatio * 100.0D,
                state.pitchWindow.mean(), pitchRatio * 100.0D, state.yawWindow.size());

        state.yawWindow.clear();
        state.pitchWindow.clear();
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}