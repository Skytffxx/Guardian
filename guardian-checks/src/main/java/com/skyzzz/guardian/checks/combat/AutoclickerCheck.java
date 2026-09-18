package com.skyzzz.guardian.checks.combat;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.AttackData;
import com.skyzzz.guardian.api.player.PlayerProfile;

/**
 * Statistical click-pattern analysis.
 *
 * Raw CPS is deliberately NOT the trigger — a jitter-clicking human can sustain
 * 16+ CPS legitimately and that is the single biggest false-positive source in
 * naive anticheats. What we measure instead:
 *
 *   1. Coefficient of variation (stdev / mean) of inter-click intervals.
 *      Humans are noisy (CV typically > 0.12); macro/timer clickers are not (< 0.05).
 *   2. Duplicate-interval density — a fixed-delay macro produces intervals that
 *      repeat to the millisecond.
 *
 * Both signals must agree, over a full window, before VL moves.
 */
public final class AutoclickerCheck extends AbstractCheck {

    public AutoclickerCheck() {
        super("autoclicker", CheckCategory.COMBAT);
    }

    @Override
    public void onAttack(PlayerProfile profile, AttackData attack) {
        long intervalMs = profile.clicks().record(attack.timestampNanos());
        if (intervalMs < 0L) {
            return;
        }

        int minSamples = i("min-samples", 25);
        if (profile.clicks().size() < minSamples) {
            return;
        }

        double minimumCps = d("minimum-cps", 8.0D);
        double mean = profile.clicks().mean();
        if (mean <= 0.0D || 1000.0D / mean < minimumCps) {
            reward(profile, d("clean-reward", 0.2D));
            return;
        }

        double cv = profile.clicks().coefficientOfVariation();
        double cvThreshold = scaled(profile, "min-coefficient-of-variation", 0.075D);
        boolean tooConsistent = cv < cvThreshold;

        int duplicates = profile.clicks().duplicateIntervalCount(i("duplicate-tolerance-ms", 2));
        double duplicateRatio = (double) duplicates / profile.clicks().size();
        boolean tooRepetitive = duplicateRatio > d("max-duplicate-ratio", 0.35D);

        // Multi-signal corroboration: both independent signals must agree.
        if (!(tooConsistent && tooRepetitive)) {
            reward(profile, d("clean-reward", 0.1D));
            return;
        }

        double severity = (cvThreshold - cv) / cvThreshold;
        flag(profile, 1.0D + severity,
                "cps=%.1f cv=%.4f (min %.4f) duplicates=%.2f%% samples=%d",
                1000.0D / mean, cv, cvThreshold, duplicateRatio * 100.0D,
                profile.clicks().size());
    }
}