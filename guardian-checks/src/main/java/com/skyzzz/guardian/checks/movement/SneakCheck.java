package com.skyzzz.guardian.checks.movement;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sneak / pose desync.
 *
 * The client reports its own sneak state, and the server tracks its own. A cheat can
 * claim "not sneaking" client-side to avoid the speed penalty while the server thinks
 * the player is crouching, or claim crouching while moving at full speed.
 *
 * Detection: the two states disagree for several ticks AND the player is moving at a
 * speed only achievable without the sneak modifier. Requires a streak because a
 * single-tick mismatch is normal at network boundaries.
 */
public final class SneakCheck extends AbstractCheck {

    private static final class State {
        int mismatchStreak;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public SneakCheck() {
        super("sneak", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMove(PlayerProfile profile, MoveData move) {
        State state = states.computeIfAbsent(profile.uuid(), k -> new State());

        if (Boolean.TRUE.equals(profile.attribute("teleport"))
                || Boolean.TRUE.equals(profile.attribute("vehicle"))
                || Boolean.TRUE.equals(profile.attribute("in-liquid"))) {
            state.mismatchStreak = 0;
            return;
        }

        boolean clientSneak = Boolean.TRUE.equals(profile.attribute("client-sneaking"));
        boolean serverSneak = Boolean.TRUE.equals(profile.attribute("server-sneaking"));

        // Mismatch plus full-speed movement is the exploit; sneak mismatch on its own is noise.
        double speed = move.horizontalDistance();
        double sneakMax = scaled(profile, "max-sneak-speed", 0.07D);

        boolean exploit = clientSneak != serverSneak && speed > sneakMax;

        if (exploit) {
            state.mismatchStreak++;
        } else {
            state.mismatchStreak = Math.max(0, state.mismatchStreak - 1);
        }

        if (state.mismatchStreak >= i("required-flags", 8)) {
            flag(profile, 1.5D,
                    "sneak desync client=%s server=%s speed=%.3f streak=%d",
                    clientSneak, serverSneak, speed, state.mismatchStreak);
            state.mismatchStreak = 0;
        }
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}