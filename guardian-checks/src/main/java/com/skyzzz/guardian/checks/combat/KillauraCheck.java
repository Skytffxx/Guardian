package com.skyzzz.guardian.checks.combat;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.AttackData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Killaura, from three independent signals: multi-target attacks in one tick, attacks
 * without a matching swing, and attacks through solid blocks. Each alone is a false
 * positive under lag, so VL only moves on a streak.
 */
public final class KillauraCheck extends AbstractCheck {

    private static final double MULTI_TARGET_WEIGHT = 2.0D;
    private static final double NO_SWING_WEIGHT = 1.5D;
    private static final double THROUGH_WALL_WEIGHT = 2.5D;

    private static final class State {
        int lastAttackTick = Integer.MIN_VALUE;
        int lastTargetId = Integer.MIN_VALUE;
        int multiTargetStreak;
        int noSwingStreak;
        int throughWallStreak;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public KillauraCheck() {
        super("killaura", CheckCategory.COMBAT);
    }

    @Override
    public void onAttack(PlayerProfile profile, AttackData attack) {
        State state = states.computeIfAbsent(profile.uuid(), key -> new State());
        int tick = (int) profile.tick();

        if (state.lastAttackTick == tick && state.lastTargetId != attack.targetEntityId()) {
            state.multiTargetStreak++;
        } else {
            state.multiTargetStreak = Math.max(0, state.multiTargetStreak - 1);
        }

        if (!attack.swung()) {
            state.noSwingStreak++;
        } else {
            state.noSwingStreak = Math.max(0, state.noSwingStreak - 1);
        }

        if (attack.targetResolved() && !attack.lineOfSight()) {
            state.throughWallStreak++;
        } else {
            state.throughWallStreak = Math.max(0, state.throughWallStreak - 1);
        }

        state.lastAttackTick = tick;
        state.lastTargetId = attack.targetEntityId();

        if (state.multiTargetStreak >= i("multi-target-streak", 2)) {
            flag(profile, MULTI_TARGET_WEIGHT, "multi-target in one tick streak=%d target=%s",
                    state.multiTargetStreak, attack.targetType());
            state.multiTargetStreak = 0;
            return;
        }

        if (state.noSwingStreak >= i("no-swing-streak", 4)) {
            flag(profile, NO_SWING_WEIGHT, "attack without swing streak=%d", state.noSwingStreak);
            state.noSwingStreak = 0;
            return;
        }

        if (state.throughWallStreak >= i("through-wall-streak", 3)) {
            flag(profile, THROUGH_WALL_WEIGHT, "attack through wall streak=%d target=%s dist=%.2f",
                    state.throughWallStreak, attack.targetType(), attack.hitboxDistance());
            state.throughWallStreak = 0;
        }
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}