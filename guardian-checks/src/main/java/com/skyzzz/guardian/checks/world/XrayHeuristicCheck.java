package com.skyzzz.guardian.checks.world;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.BlockBreakData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Xray heuristic: ore ratio in a mining window. The signal is inherently probabilistic
 * — caving, branch mining and plain luck look alike — so the check ships disabled for
 * punishment (see {@code review-only}) with a high threshold and an empty command list.
 */
public final class XrayHeuristicCheck extends AbstractCheck {

    private static final class State {
        int oreBreaks;
        int totalBreaks;
        double straightLineDistance;
        long windowStartNanos;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public XrayHeuristicCheck() {
        super("xray", CheckCategory.WORLD);
    }

    @Override
    public void onBlockBreak(PlayerProfile profile, BlockBreakData breakData) {
        if (!b("review-only", true)) {
            return;
        }
        State state = states.computeIfAbsent(profile.uuid(), k -> new State());
        long now = breakData.timestampNanos();

        if (state.windowStartNanos == 0L) {
            state.windowStartNanos = now;
        }
        double windowSeconds = d("window-seconds", 600.0D);
        if ((now - state.windowStartNanos) / 1_000_000_000.0D > windowSeconds) {
            state.oreBreaks = 0;
            state.totalBreaks = 0;
            state.windowStartNanos = now;
        }

        state.totalBreaks++;
        if (ore(breakData.material())) {
            state.oreBreaks++;
        }

        if (state.totalBreaks < i("minimum-breaks", 60)) {
            return;
        }

        double oreRatio = (double) state.oreBreaks / state.totalBreaks;
        double threshold = d("ore-ratio-threshold", 0.55D);

        if (oreRatio > threshold) {
            flag(profile, 1.0D, "REVIEW-ONLY ore ratio=%.3f (%d/%d) in %.0fs",
                    oreRatio, state.oreBreaks, state.totalBreaks, windowSeconds);
            state.oreBreaks = 0;
            state.totalBreaks = 0;
            state.windowStartNanos = now;
        }
    }

    private boolean ore(String material) {
        return material.endsWith("_ORE")
                || material.equals("ANCIENT_DEBRIS")
                || material.equals("DEEPSLATE_DIAMOND_ORE");
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}