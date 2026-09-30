package com.skyzzz.guardian.checks.movement;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gravity bypass and hover: sustained air time with no downward acceleration, or a
 * plateau where deltaY stays near zero while airborne. Both are suppressed in liquids,
 * on ladders, while levitating, gliding, riding, or while the server is lagging.
 */
public final class FlyCheck extends AbstractCheck {

    private static final class State {
        int airTicks;
        int hoverTicks;
        double lastDeltaY;
        int gravityViolations;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public FlyCheck() {
        super("fly", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMove(PlayerProfile profile, MoveData move) {
        State state = states.computeIfAbsent(profile.uuid(), key -> new State());

        if (exempt(profile)) {
            clearAirborne(state);
            return;
        }

        if (move.onGround()) {
            clearAirborne(state);
            state.lastDeltaY = 0.0D;
            reward(profile, d("clean-reward", 0.1D));
            return;
        }

        state.airTicks++;
        double deltaY = move.deltaY();

        if (deltaY < -d("falling-threshold", 0.12D)) {
            state.hoverTicks = 0;
        } else if (state.airTicks > i("minimum-air-ticks", 8)) {
            state.hoverTicks++;
        }

        if (state.airTicks > i("gravity-grace-ticks", 6)
                && deltaY >= -d("gravity-minimum-fall", 0.02D)
                && Math.abs(deltaY - state.lastDeltaY) < d("gravity-max-delta-change", 0.005D)) {
            state.gravityViolations++;
        } else if (state.gravityViolations > 0) {
            state.gravityViolations--;
        }

        state.lastDeltaY = deltaY;

        if (state.hoverTicks >= i("hover-ticks", 14)) {
            flag(profile, 2.0D, "hover ticks=%d deltaY=%.5f airTicks=%d",
                    state.hoverTicks, deltaY, state.airTicks);
            state.hoverTicks = 0;
            return;
        }

        if (state.gravityViolations >= i("gravity-violations", 12)) {
            flag(profile, 1.5D, "no gravity accel violations=%d deltaY=%.5f",
                    state.gravityViolations, deltaY);
            state.gravityViolations = 0;
        }
    }

    private void clearAirborne(State state) {
        state.airTicks = 0;
        state.hoverTicks = 0;
        state.gravityViolations = 0;
    }

    private boolean exempt(PlayerProfile profile) {
        return Boolean.TRUE.equals(profile.attribute("in-liquid"))
                || Boolean.TRUE.equals(profile.attribute("on-climbable"))
                || Boolean.TRUE.equals(profile.attribute("levitating"))
                || Boolean.TRUE.equals(profile.attribute("elytra"))
                || Boolean.TRUE.equals(profile.attribute("vehicle"))
                || Boolean.TRUE.equals(profile.attribute("riptide"))
                || Boolean.TRUE.equals(profile.attribute("teleport"))
                || Boolean.TRUE.equals(profile.attribute("levitation-effect"))
                || Boolean.TRUE.equals(profile.attribute("slow-falling"))
                || Boolean.TRUE.equals(profile.attribute("near-vehicle"));
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}