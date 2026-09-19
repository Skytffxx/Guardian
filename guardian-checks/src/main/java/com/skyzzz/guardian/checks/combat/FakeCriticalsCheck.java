package com.skyzzz.guardian.checks.combat;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.AttackData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fake criticals.
 *
 * True crit damage is computed server-side, so a client cannot "claim" a crit at the
 * packet layer. What it CAN do is desync its sprint state: send START_SPRINTING to
 * get the knockback multiplier applied client-side while the server refuses to set
 * sprinting (low hunger, blocked by collision). The result is that the client plays
 * a sprint-knockback attack that the server never applied.
 *
 * Detection: when the client claims sprinting but the server disagrees, and an attack
 * lands inside that mismatch window, flag. A single-tick mismatch is common during
 * normal desync; require a streak.
 */
public final class FakeCriticalsCheck extends AbstractCheck {

    private static final class State {
        int mismatchStreak;
        long lastAttackTick = Long.MIN_VALUE;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public FakeCriticalsCheck() {
        super("fakecriticals", CheckCategory.COMBAT);
    }

    @Override
    public void onMove(PlayerProfile profile, com.skyzzz.guardian.api.data.MoveData move) {
        State state = states.computeIfAbsent(profile.uuid(), k -> new State());

        boolean clientSprint = Boolean.TRUE.equals(profile.attribute("client-sprinting"));
        boolean serverSprint = Boolean.TRUE.equals(profile.attribute("server-sprinting"));

        if (clientSprint && !serverSprint) {
            state.mismatchStreak++;
        } else {
            state.mismatchStreak = Math.max(0, state.mismatchStreak - 1);
        }
    }

    @Override
    public void onAttack(PlayerProfile profile, AttackData attack) {
        State state = states.computeIfAbsent(profile.uuid(), k -> new State());

        long currentTick = profile.tick();
        if (currentTick == state.lastAttackTick) {
            return;
        }
        state.lastAttackTick = currentTick;

        int required = i("required-mismatch-streak", 6);
        if (state.mismatchStreak < required) {
            return;
        }

        // Only flag if the attack would plausibly carry a crit effect (airborne or sprinting).
        if (attack.swung() && !Boolean.TRUE.equals(profile.attribute("server-on-ground"))) {
            flag(profile, 2.0D,
                    "client sprinting while server refused sprint for %d ticks at attack target=%s",
                    state.mismatchStreak, attack.targetType());
            state.mismatchStreak = 0;
        }
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}