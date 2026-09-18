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
 * Aim analysis via rotation GCD.
 *
 * A mouse produces rotation deltas that are effectively irrational multiples of the
 * sensitivity step, so the GCD of consecutive deltas collapses toward zero. A cheat
 * that computes rotations from a fixed step (or snaps to a target) produces deltas
 * that all share a large common divisor. We track the GCD across a window and flag
 * only when it stays suspiciously high for many consecutive samples — cinematic
 * camera and Bedrock touch input are explicitly tolerated via thresholds.
 */
public final class AimCheck extends AbstractCheck {

    private static final class State {
        double lastYawDelta;
        double lastPitchDelta;
        double gcdYaw = 0.0D;
        double gcdPitch = 0.0D;
        final RollingWindow yawGcdWindow;
        final RollingWindow pitchGcdWindow;

        State(int window) {
            this.yawGcdWindow = new RollingWindow(window);
            this.pitchGcdWindow = new RollingWindow(window);
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
        // Ignore teleports/vehicle exits, which produce huge single-tick deltas.
        if (move.yawDeltaAbs() > d("ignore-above-yaw-delta", 90.0F)
                || move.pitchDeltaAbs() > d("ignore-above-pitch-delta", 60.0F)) {
            states.remove(profile.uuid());
            return;
        }

        State state = states.computeIfAbsent(profile.uuid(),
                key -> new State(i("window-size", 40)));

        double yawDelta = move.deltaYaw();
        double pitchDelta = move.deltaPitch();

        if (state.lastYawDelta != 0.0D && yawDelta != 0.0D) {
            state.gcdYaw = MathUtil.gcd(state.lastYawDelta, yawDelta);
            state.yawGcdWindow.add(state.gcdYaw);
        }
        if (state.lastPitchDelta != 0.0D && pitchDelta != 0.0D) {
            state.gcdPitch = MathUtil.gcd(state.lastPitchDelta, pitchDelta);
            state.pitchGcdWindow.add(state.gcdPitch);
        }

        state.lastYawDelta = yawDelta;
        state.lastPitchDelta = pitchDelta;

        if (!state.yawGcdWindow.isFull() || !state.pitchGcdWindow.isFull()) {
            return;
        }

        double gcdThreshold = scaled(profile, "min-gcd", 0.009D);
        double requiredRatio = d("required-above-ratio", 0.72D);

        double yawRatio = (double) state.yawGcdWindow.countAbove(gcdThreshold)
                / state.yawGcdWindow.size();
        double pitchRatio = (double) state.pitchGcdWindow.countAbove(gcdThreshold)
                / state.pitchGcdWindow.size();

        if (yawRatio < requiredRatio || pitchRatio < requiredRatio) {
            reward(profile, d("clean-reward", 0.2D));
            return;
        }

        flag(profile, 1.0D + (yawRatio + pitchRatio) / 2.0D,
                "gcdYaw=%.5f (%.0f%%) gcdPitch=%.5f (%.0f%%) window=%d",
                state.yawGcdWindow.mean(), yawRatio * 100.0D,
                state.pitchGcdWindow.mean(), pitchRatio * 100.0D,
                state.yawGcdWindow.size());

        state.yawGcdWindow.clear();
        state.pitchGcdWindow.clear();
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}