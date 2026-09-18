package com.skyzzz.guardian.checks.world;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.BlockPlaceData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scaffold / tower. Two signals:
 *   1. Rotation-while-placing: the player places a block they cannot see from their
 *      reported look vector (classic "silent rotation" scaffold).
 *   2. Tower placement below the feet with a pitch that is not downward enough.
 *
 * Bedrock gets a much wider angular tolerance because touch placement genuinely
 * produces different aim timing. Full exemption would be a labelled bypass, so we
 * tune instead.
 */
public final class ScaffoldCheck extends AbstractCheck {

    private static final class State {
        int rotationMissStreak;
        int towerMissStreak;
        long lastPlaceNanos;
        int placesInWindow;
        int rapidPlaceStreak;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public ScaffoldCheck() {
        super("scaffold", CheckCategory.WORLD);
    }

    @Override
    public void onBlockPlace(PlayerProfile profile, BlockPlaceData place) {
        State state = states.computeIfAbsent(profile.uuid(), key -> new State());

        if (Boolean.TRUE.equals(profile.attribute("creative"))
                || Boolean.TRUE.equals(profile.attribute("teleport"))) {
            state.rotationMissStreak = 0;
            state.towerMissStreak = 0;
            return;
        }

        // --- signal 1: placing a block that is not in the look direction ---
        if (!place.lineOfSight()
                && place.eyeDistance() > d("line-of-sight-minimum-distance", 1.0D)) {
            state.rotationMissStreak++;
        } else {
            state.rotationMissStreak = Math.max(0, state.rotationMissStreak - 1);
        }

        // --- signal 2: tower placement with insufficient downward pitch ---
        boolean placedBelowFeet = place.blockY() < place.eyeY() - d("below-feet-threshold", 1.2D);
        if (placedBelowFeet
                && place.pitch() > -scaled(profile, "tower-minimum-pitch", 55.0F)) {
            state.towerMissStreak++;
        } else {
            state.towerMissStreak = Math.max(0, state.towerMissStreak - 1);
        }

        // --- signal 3: superhuman placement cadence ---
        if (state.lastPlaceNanos != 0L) {
            double intervalMs = (place.timestampNanos() - state.lastPlaceNanos) / 1_000_000.0D;
            if (intervalMs < d("minimum-interval-ms", 85.0D)) {
                state.rapidPlaceStreak++;
            } else {
                state.rapidPlaceStreak = Math.max(0, state.rapidPlaceStreak - 1);
            }
        }
        state.lastPlaceNanos = place.timestampNanos();

        if (state.rotationMissStreak >= i("rotation-miss-streak", 4)) {
            flag(profile, 1.5D, "place without line of sight streak=%d dist=%.2f yaw=%.2f pitch=%.2f",
                    state.rotationMissStreak, place.eyeDistance(), place.yaw(), place.pitch());
            state.rotationMissStreak = 0;
            return;
        }

        if (state.towerMissStreak >= i("tower-miss-streak", 4)) {
            flag(profile, 2.0D, "tower placement pitch=%.2f streak=%d",
                    place.pitch(), state.towerMissStreak);
            state.towerMissStreak = 0;
            return;
        }

        if (state.rapidPlaceStreak >= i("rapid-place-streak", 8)) {
            flag(profile, 1.0D, "rapid placement streak=%d", state.rapidPlaceStreak);
            state.rapidPlaceStreak = 0;
        }
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}