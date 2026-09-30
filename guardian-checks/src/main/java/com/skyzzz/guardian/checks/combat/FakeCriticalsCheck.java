package com.skyzzz.guardian.checks.combat;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.AttackData;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sprint desync. Crit damage itself is computed server-side, so a client cannot claim
 * a crit at the packet layer — but it can send START_SPRINTING to get the knockback
 * multiplier applied client-side while the server refuses to set sprinting (low hunger,
 * blocked by collision). When the client claims sprinting and the server disagrees for
 * a whole streak, an attack landing inside that window is the cheat.
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
    public void onMove(PlayerProfile profile, MoveData move) {
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

        long tick = profile.tick();
        if (tick == state.lastAttackTick) {
            return;
        }
        state.lastAttackTick = tick;

        if (state.mismatchStreak < i("required-mismatch-streak", 6)) {
            return;
        }

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