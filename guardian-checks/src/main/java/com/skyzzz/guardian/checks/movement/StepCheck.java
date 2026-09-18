package com.skyzzz.guardian.checks.movement;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Illegal step height. Vanilla step is 0.6 (0.5 + epsilon); jump-boost and
 * soul-speed raise it legitimately. We require the climb to repeat before flagging,
 * because stairs, slabs and lag all produce one-off jumps.
 */
public final class StepCheck extends AbstractCheck {

    private static final class State {
        int streak;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public StepCheck() {
        super("step", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMove(PlayerProfile profile, MoveData move) {
        State state = states.computeIfAbsent(profile.uuid(), key -> new State());

        if (Boolean.TRUE.equals(profile.attribute("in-liquid"))
                || Boolean.TRUE.equals(profile.attribute("on-climbable"))
                || Boolean.TRUE.equals(profile.attribute("vehicle"))
                || Boolean.TRUE.equals(profile.attribute("teleport"))
                || Boolean.TRUE.equals(profile.attribute("levitating"))) {
            state.streak = 0;
            return;
        }

        double rise = move.deltaY();
        double maxStep = d("max-step", 0.6D);

        int jumpBoost = profile.attribute("jump-boost-amplifier") instanceof Number number
                ? number.intValue() : 0;
        maxStep += jumpBoost * d("step-per-jump-boost-level", 0.1D);
        if (Boolean.TRUE.equals(profile.attribute("soul-speed"))) {
            maxStep += d("soul-speed-step-bonus", 0.35D);
        }
        maxStep += d("buffer", 0.02D);

        boolean illegalStep = rise > maxStep
                && move.onGround()
                && move.lastOnGround()
                && move.horizontalDistance() > d("minimum-horizontal", 0.01D);

        if (illegalStep) {
            state.streak++;
        } else {
            state.streak = Math.max(0, state.streak - 1);
        }

        if (state.streak >= i("required-flags", 2)) {
            flag(profile, 1.5D, "step=%.4f max=%.4f horizontal=%.4f",
                    rise, maxStep, move.horizontalDistance());
            state.streak = 0;
        }
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}