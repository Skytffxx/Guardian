package com.skyzzz.guardian.checks.movement;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Elytra exploits: firework boosts claimed while not gliding, and boosts fired faster
 * than the item's use duration allows.
 */
public final class ElytraCheck extends AbstractCheck {

    private static final class State {
        long lastFireworkTick = Long.MIN_VALUE;
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

        boolean gliding = Boolean.TRUE.equals(profile.attribute("elytra"));
        Object boostAttr = profile.attribute("firework-boost-tick");
        long boostTick = boostAttr instanceof Long l ? l : Long.MIN_VALUE;

        if (boostTick != state.lastFireworkTick && boostTick != Long.MIN_VALUE) {
            if (!gliding) {
                state.illegalBoostStreak++;
            } else {
                state.illegalBoostStreak = Math.max(0, state.illegalBoostStreak - 1);
            }

            if (state.lastFireworkTick != Long.MIN_VALUE
                    && profile.tick() - state.lastFireworkTick < i("minimum-boost-gap-ticks", 8)) {
                state.rapidBoostStreak++;
            } else {
                state.rapidBoostStreak = Math.max(0, state.rapidBoostStreak - 1);
            }

            state.lastFireworkTick = boostTick;
        }

        if (state.illegalBoostStreak >= i("illegal-boost-streak", 2)) {
            flag(profile, 2.5D, "firework boost while not gliding (streak=%d)",
                    state.illegalBoostStreak);
            state.illegalBoostStreak = 0;
            return;
        }

        if (state.rapidBoostStreak >= i("rapid-boost-streak", 3)) {
            flag(profile, 2.0D, "rapid firework boost streak=%d (gap < %d ticks)",
                    state.rapidBoostStreak, i("minimum-boost-gap-ticks", 8));
            state.rapidBoostStreak = 0;
        }
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}