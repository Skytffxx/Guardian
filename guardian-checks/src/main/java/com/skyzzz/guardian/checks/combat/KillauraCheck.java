package com.skyzzz.guardian.checks.combat;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.AttackData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Killaura, detected through three independent signals:
 *   - multi-target attacks inside one tick,
 *   - attacks with no matching swing animation,
 *   - attacking entities through solid blocks.
 * Any one signal alone can be a false positive under lag, so VL only moves when
 * the same signal repeats inside its rolling window.
 */
public final class KillauraCheck extends AbstractCheck {

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
        int currentTick = (int) profile.tick();

        if (state.lastAttackTick == currentTick
                && state.lastTargetId != attack.targetEntityId()) {
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

        state.lastAttackTick = currentTick;
        state.lastTargetId = attack.targetEntityId();

        int multiTargetLimit = i("multi-target-streak", 2);
        int noSwingLimit = i("no-swing-streak", 4);
        int throughWallLimit = i("through-wall-streak", 3);

        if (state.multiTargetStreak >= multiTargetLimit) {
            flag(profile, 2.0D, "multi-target in one tick streak=%d target=%s",
                    state.multiTargetStreak, attack.targetType());
            state.multiTargetStreak = 0;
            return;
        }

        if (state.noSwingStreak >= noSwingLimit) {
            flag(profile, 1.5D, "attack without swing streak=%d", state.noSwingStreak);
            state.noSwingStreak = 0;
            return;
        }

        if (state.throughWallStreak >= throughWallLimit) {
            flag(profile, 2.5D, "attack through wall streak=%d target=%s dist=%.2f",
                    state.throughWallStreak, attack.targetType(), attack.hitboxDistance());
            state.throughWallStreak = 0;
        }
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}