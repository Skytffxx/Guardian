package com.skyzzz.guardian.checks.movement;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client claims onGround while the server sees it airborne with real fall distance
 * banked. One mis-synced tick is normal, so a streak is required.
 */
public final class NoFallCheck extends AbstractCheck {

    private static final class State {
        int consecutive;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public NoFallCheck() {
        super("nofall", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMove(PlayerProfile profile, MoveData move) {
        State state = states.computeIfAbsent(profile.uuid(), key -> new State());

        if (exempt(profile)) {
            state.consecutive = 0;
            return;
        }

        Object fallAttr = profile.attribute("fall-distance");
        double fallDistance = fallAttr instanceof Number n ? n.doubleValue() : 0.0D;

        boolean falseGroundClaim = move.onGround()
                && !Boolean.TRUE.equals(profile.attribute("server-on-ground"))
                && fallDistance > d("minimum-fall-distance", 1.5D);

        if (falseGroundClaim) {
            state.consecutive++;
        } else {
            state.consecutive = Math.max(0, state.consecutive - 1);
        }

        if (state.consecutive >= i("required-flags", 3)) {
            flag(profile, 1.5D, "false onGround claim streak=%d fallDistance=%.2f deltaY=%.4f",
                    state.consecutive, fallDistance, move.deltaY());
            state.consecutive = 0;
        }
    }

    private boolean exempt(PlayerProfile profile) {
        return Boolean.TRUE.equals(profile.attribute("in-liquid"))
                || Boolean.TRUE.equals(profile.attribute("on-climbable"))
                || Boolean.TRUE.equals(profile.attribute("elytra"))
                || Boolean.TRUE.equals(profile.attribute("vehicle"))
                || Boolean.TRUE.equals(profile.attribute("teleport"));
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}