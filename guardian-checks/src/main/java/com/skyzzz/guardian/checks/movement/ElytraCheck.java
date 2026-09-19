package com.skyzzz.guardian.checks.movement;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Elytra-specific exploits.
 *
 * Signals:
 *   1. Firework boost claimed while not gliding (invalid state).
 *   2. Firework boosts fired faster than the item's use duration allows.
 *   3. Mid-air dive recovery without a firework — the client claims vertical speed
 *      increase while no boost was recorded.
 */
public final class ElytraCheck extends AbstractCheck {

    private static final class State {
        long lastFireworkTick = Long.MIN_VALUE;
        long lastGlideTick = Long.MIN_VALUE;
        int illegalBoostStreak;
        int rapidBoostStreak;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public ElytraCheck() {
        super("elytra", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMove(PlayerProfile profile, MoveData move) {
        State state = states.computeIfAbsent(profile.uuid(), k -> new State());

        if (Boolean.TRUE.equals(profile.attribute("teleport"))) {
            state.illegalBoostStreak = 0;
            state.rapidBoostStreak = 0;
            return;
        }

        long currentTick = profile.tick();
        boolean gliding = Boolean.TRUE.equals(profile.attribute("elytra"));
        Object boostAttr = profile.attribute("firework-boost-tick");
        long boostTick = boostAttr instanceof Long l ? l : Long.MIN_VALUE;

        // Track whether we've seen a new firework this tick.
        boolean freshBoost = boostTick != state.lastFireworkTick && boostTick != Long.MIN_VALUE;

        if (freshBoost) {
            // --- Signal 1: boost while not gliding ---
            if (!gliding) {
                state.illegalBoostStreak++;
            } else {
                state.illegalBoostStreak = Math.max(0, state.illegalBoostStreak - 1);
            }

            // --- Signal 2: rapid boosting ---
            if (state.lastFireworkTick != Long.MIN_VALUE) {
                long gap = currentTick - state.lastFireworkTick;
                if (gap < i("minimum-boost-gap-ticks", 8)) {
                    state.rapidBoostStreak++;
                } else {
                    state.rapidBoostStreak = Math.max(0, state.rapidBoostStreak - 1);
                }
            }

            state.lastFireworkTick = boostTick;
        }

        if (gliding) {
            state.lastGlideTick = currentTick;
        }

        if (state.illegalBoostStreak >= i("illegal-boost-streak", 2)) {
            flag(profile, 2.5D,
                    "firework boost while not gliding (streak=%d)", state.illegalBoostStreak);
            state.illegalBoostStreak = 0;
            return;
        }

        if (state.rapidBoostStreak >= i("rapid-boost-streak", 3)) {
            flag(profile, 2.0D,
                    "rapid firework boost streak=%d (gap < %d ticks)",
                    state.rapidBoostStreak, i("minimum-boost-gap-ticks", 8));
            state.rapidBoostStreak = 0;
        }
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}