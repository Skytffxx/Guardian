package com.skyzzz.guardian.checks.combat;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.AttackData;
import com.skyzzz.guardian.api.player.PlayerProfile;

/**
 * Click-pattern statistics. Raw CPS is never the trigger: jitter-clicking humans
 * sustain 16+ CPS legitimately, which is the biggest false-positive source in naive
 * anticheats. Instead the coefficient of variation and duplicate-interval density are
 * measured together, and both must agree across a full window before VL moves.
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
        if (profile.clicks().size() < i("min-samples", 25)) {
            return;
        }

        double mean = profile.clicks().mean();
        if (mean <= 0.0D || 1000.0D / mean < d("minimum-cps", 8.0D)) {
            reward(profile, d("clean-reward", 0.2D));
            return;
        }

        double cvThreshold = scaled(profile, "min-coefficient-of-variation", 0.075D);
        double cv = profile.clicks().coefficientOfVariation();
        int duplicates = profile.clicks().duplicateIntervalCount(i("duplicate-tolerance-ms", 2));
        double duplicateRatio = (double) duplicates / profile.clicks().size();

        if (cv >= cvThreshold || duplicateRatio <= d("max-duplicate-ratio", 0.35D)) {
            reward(profile, d("clean-reward", 0.1D));
            return;
        }

        flag(profile, 1.0D + (cvThreshold - cv) / cvThreshold,
                "cps=%.1f cv=%.4f (min %.4f) duplicates=%.2f%% samples=%d",
                1000.0D / mean, cv, cvThreshold, duplicateRatio * 100.0D,
                profile.clicks().size());
    }
}