package com.skyzzz.guardian.checks.world;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.BlockBreakData;
import com.skyzzz.guardian.api.player.PlayerProfile;

/**
 * Xray heuristic: ore-beeline pathing — mining straight to ores with minimal
 * surrounding-block excavation.
 *
 * THIS CHECK NEVER AUTO-PUNISHES. It raises VL that maps only to the "review"
 * punishment tier, which by default is a staff alert and nothing else. The signal is
 * inherently probabilistic (caving, branch mining and plain luck all look similar),
 * so the config ships it with a very high threshold and the punishment list empty.
 */
public final class XrayHeuristicCheck extends AbstractCheck {

    private static final class State {
        int oreBreaks;
        int totalBreaks;
        double straightLineDistance;
        long windowStartNanos;
    }

    private final java.util.Map<java.util.UUID, State> states = new java.util.concurrent.ConcurrentHashMap<>();

    public XrayHeuristicCheck() {
        super("xray", CheckCategory.WORLD);
    }

    @Override
    public void onBlockBreak(PlayerProfile profile, BlockBreakData breakData) {
        if (!b("review-only", true)) {
            return;
        }
        State state = states.computeIfAbsent(profile.uuid(), key -> new State());
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
        if (isOre(breakData.material())) {
            state.oreBreaks++;
        }

        int minimumBreaks = i("minimum-breaks", 60);
        if (state.totalBreaks < minimumBreaks) {
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

    private boolean isOre(String material) {
        return material.endsWith("_ORE")
                || material.equals("ANCIENT_DEBRIS")
                || material.equals("DEEPSLATE_DIAMOND_ORE");
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}