package com.skyzzz.guardian.checks.movement;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NoClip: the player's volume sits inside a solid block. A block update on the same
 * tick as a move overlaps legitimately, so a streak is required.
 */
public final class PhaseCheck extends AbstractCheck {

    private static final class State {
        int phaseStreak;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public PhaseCheck() {
        super("phase", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMove(PlayerProfile profile, MoveData move) {
        State state = states.computeIfAbsent(profile.uuid(), k -> new State());

        if (Boolean.TRUE.equals(profile.attribute("creative"))
                || Boolean.TRUE.equals(profile.attribute("spectator"))
                || Boolean.TRUE.equals(profile.attribute("teleport"))
                || Boolean.TRUE.equals(profile.attribute("vehicle"))) {
            state.phaseStreak = 0;
            return;
        }

        boolean feetInSolid = Boolean.TRUE.equals(profile.attribute("feet-in-solid"));
        boolean headInSolid = Boolean.TRUE.equals(profile.attribute("head-in-solid"));

        if (feetInSolid || headInSolid) {
            state.phaseStreak++;
        } else {
            state.phaseStreak = Math.max(0, state.phaseStreak - 2);
        }

        if (state.phaseStreak >= i("required-flags", 3)) {
            flag(profile, 3.0D, "inside solid block streak=%d feet=%s head=%s at %.1f/%.1f/%.1f",
                    state.phaseStreak, feetInSolid, headInSolid, move.x(), move.y(), move.z());
            state.phaseStreak = 0;
        }
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}