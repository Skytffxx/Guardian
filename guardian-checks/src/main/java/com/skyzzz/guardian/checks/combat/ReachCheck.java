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
 * Reach against the real hitbox. Core hands over the true eye-to-box distance, so
 * client-reported positions cannot fool it. One packet never flags.
 */
public final class ReachCheck extends AbstractCheck {

    private final Map<UUID, RollingWindow> windows = new ConcurrentHashMap<>();

    public ReachCheck() {
        super("reach", CheckCategory.COMBAT);
    }

    @Override
    public void onAttack(PlayerProfile profile, AttackData attack) {
        if (!attack.targetResolved()) {
            return;
        }
        // Attacks through walls belong to killaura.
        if (!attack.lineOfSight()) {
            return;
        }

        double allowed = scaled(profile, "max-reach", 3.0D)
                + d("buffer", 0.08D)
                + pingCompensation(profile);
        double excess = attack.hitboxDistance() - allowed;

        RollingWindow window = windows.computeIfAbsent(profile.uuid(),
                key -> new RollingWindow(i("window-size", 12)));

        if (excess <= 0.0D) {
            window.add(0.0D);
            reward(profile, d("clean-reward", 0.15D));
            return;
        }

        window.add(excess);
        if (!window.isFull()) {
            return;
        }

        int over = window.countAbove(d("minimum-excess", 0.03D));
        if (over < i("required-flags", 5)) {
            return;
        }

        double weight = Math.min(d("max-weight", 3.0D), excess / d("weight-per-block", 0.25D));
        flag(profile, Math.max(1.0D, weight),
                "dist=%.3f allowed=%.3f excess=%.3f ping=%dms over=%d/%d",
                attack.hitboxDistance(), allowed, excess, profile.ping(), over, window.size());
    }

    private double pingCompensation(PlayerProfile profile) {
        double perMs = d("ping-compensation-per-ms", 0.0022D);
        return Math.min(d("ping-compensation-cap", 0.35D), profile.ping() * perMs);
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        windows.remove(profile.uuid());
    }
}