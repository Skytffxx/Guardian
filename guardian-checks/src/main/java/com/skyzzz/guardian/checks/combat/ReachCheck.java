package com.skyzzz.guardian.checks.combat;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.AttackData;
import com.skyzzz.guardian.api.player.PlayerProfile;
import com.skyzzz.guardian.api.util.RollingWindow;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real hitbox-vs-eye reach, ping- and interpolation-adjusted.
 *
 * Core resolves the target's bounding box and hands us the true eye-to-box distance,
 * so this check is pure arithmetic and cannot be fooled by client-reported positions.
 * A single packet never flags: we require a rolling window of consecutive over-reach
 * attacks before VL moves.
 */
public final class ReachCheck extends AbstractCheck {

    private final Map<UUID, RollingWindow> excessWindows = new ConcurrentHashMap<>();

    public ReachCheck() {
        super("reach", CheckCategory.COMBAT);
    }

    @Override
    public void onAttack(PlayerProfile profile, AttackData attack) {
        if (!attack.targetResolved()) {
            return;
        }
        // Never evaluate through walls here — that is killaura's job.
        if (!attack.lineOfSight()) {
            return;
        }

        double maxReach = scaled(profile, "max-reach", 3.0D);
        double buffer = d("buffer", 0.08D);
        double pingCompensation = pingCompensation(profile);

        double allowed = maxReach + buffer + pingCompensation;
        double excess = attack.hitboxDistance() - allowed;

        RollingWindow window = excessWindows.computeIfAbsent(
                profile.uuid(), key -> new RollingWindow(i("window-size", 12)));

        if (excess <= 0.0D) {
            window.add(0.0D);
            reward(profile, d("clean-reward", 0.15D));
            return;
        }

        window.add(excess);

        int requiredFlags = i("required-flags", 5);
        double minimumExcess = d("minimum-excess", 0.03D);

        if (!window.isFull()) {
            return;
        }

        int over = window.countAbove(minimumExcess);
        if (over < requiredFlags) {
            return;
        }

        double weight = Math.min(d("max-weight", 3.0D), excess / d("weight-per-block", 0.25D));
        flag(profile, Math.max(1.0D, weight),
                "dist=%.3f allowed=%.3f excess=%.3f ping=%dms over=%d/%d",
                attack.hitboxDistance(), allowed, excess, profile.ping(),
                over, window.size());
    }

    private double pingCompensation(PlayerProfile profile) {
        double perMs = d("ping-compensation-per-ms", 0.0022D);
        double cap = d("ping-compensation-cap", 0.35D);
        return Math.min(cap, profile.ping() * perMs);
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        excessWindows.remove(profile.uuid());
    }
}