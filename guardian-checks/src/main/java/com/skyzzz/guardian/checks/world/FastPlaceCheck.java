package com.skyzzz.guardian.checks.world;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.BlockPlaceData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FastPlace / FastBreak at the packet level: sustained placement intervals below the
 * vanilla client tick floor. Requires a streak because a single fast place is normal
 * when two placements land in the same tick after a lag catch-up.
 */
public final class FastPlaceCheck extends AbstractCheck {

    private static final class State {
        long lastPlaceNanos;
        int streak;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public FastPlaceCheck() {
        super("fastplace", CheckCategory.WORLD);
    }

    @Override
    public void onBlockPlace(PlayerProfile profile, BlockPlaceData place) {
        State state = states.computeIfAbsent(profile.uuid(), key -> new State());

        if (state.lastPlaceNanos == 0L) {
            state.lastPlaceNanos = place.timestampNanos();
            return;
        }

        double intervalMs = (place.timestampNanos() - state.lastPlaceNanos) / 1_000_000.0D;
        state.lastPlaceNanos = place.timestampNanos();

        if (intervalMs <= 0.0D || intervalMs > d("ignore-above-ms", 500.0D)) {
            state.streak = 0;
            return;
        }

        double minimum = scaled(profile, "minimum-interval-ms", 45.0D);
        if (intervalMs < minimum) {
            state.streak++;
        } else {
            state.streak = Math.max(0, state.streak - 1);
        }

        if (state.streak >= i("required-flags", 5)) {
            flag(profile, 1.0D, "interval=%.2fms minimum=%.2fms streak=%d",
                    intervalMs, minimum, state.streak);
            state.streak = 0;
        }
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}