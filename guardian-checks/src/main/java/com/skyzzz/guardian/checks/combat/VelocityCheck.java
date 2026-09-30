package com.skyzzz.guardian.checks.combat;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Knockback manipulation. Core stores the expected per-tick delta from the outbound
 * velocity packet on the profile; this tracks the actual delta over the next few ticks.
 * A ratio consistently well below 1.0 means the client is refusing knockback.
 */
public final class VelocityCheck extends AbstractCheck {

    private static final class State {
        long expectedTick = Long.MIN_VALUE;
        double expectedX;
        double expectedZ;
        double actualX;
        double actualZ;
        int sampleTicks;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public VelocityCheck() {
        super("velocity", CheckCategory.COMBAT);
    }

    @Override
    public void onMove(PlayerProfile profile, MoveData move) {
        State state = states.computeIfAbsent(profile.uuid(), k -> new State());

        Object tickAttr = profile.attribute("velocity-tick");
        if (tickAttr instanceof Long sentTick && sentTick != state.expectedTick) {
            state.expectedTick = sentTick;
            state.expectedX = numAttr(profile, "velocity-x", 0.0D);
            state.expectedZ = numAttr(profile, "velocity-z", 0.0D);
            state.actualX = 0.0D;
            state.actualZ = 0.0D;
            state.sampleTicks = 0;
            return;
        }

        if (exempt(profile, move) || state.expectedTick == Long.MIN_VALUE) {
            return;
        }

        // Bedrock knockback reads weaker due to input latency, so widen the expectation.
        double knockbackScale = profile.isBedrock() ? d("bedrock-knockback-scale", 0.8D) : 1.0D;
        double expected = Math.sqrt(state.expectedX * state.expectedX
                + state.expectedZ * state.expectedZ) * 0.5D * knockbackScale;
        if (expected < d("minimum-expected-motion", 0.15D)) {
            return;
        }

        state.actualX += move.deltaX();
        state.actualZ += move.deltaZ();
        state.sampleTicks++;

        if (state.sampleTicks < i("sample-window", 6)) {
            return;
        }

        double actual = Math.sqrt(state.actualX * state.actualX + state.actualZ * state.actualZ);
        double expectedTotal = expected * state.sampleTicks;
        double ratio = actual / expectedTotal;

        if (ratio < scaled(profile, "minimum-ratio", 0.55D)) {
            flag(profile, 2.0D, "velocity applied=%.3f expected=%.3f ratio=%.2f over %d ticks",
                    actual, expectedTotal, ratio, state.sampleTicks);
        } else {
            reward(profile, d("clean-reward", 0.2D));
        }

        state.expectedTick = Long.MIN_VALUE;
    }

    /** States in which the client may legitimately ignore knockback. */
    private boolean exempt(PlayerProfile profile, MoveData move) {
        return Boolean.TRUE.equals(profile.attribute("teleport"))
                || Boolean.TRUE.equals(profile.attribute("vehicle"))
                || Boolean.TRUE.equals(profile.attribute("in-liquid"))
                || Boolean.TRUE.equals(profile.attribute("on-climbable"))
                || Boolean.TRUE.equals(profile.attribute("elytra"))
                || Boolean.TRUE.equals(profile.attribute("levitating"))
                || (move.onGround() && move.lastOnGround());
    }

    private double numAttr(PlayerProfile profile, String key, double fallback) {
        Object value = profile.attribute(key);
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}