package com.skyzzz.guardian.checks.world;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.BlockBreakData;
import com.skyzzz.guardian.api.data.BlockPlaceData;
import com.skyzzz.guardian.api.player.PlayerProfile;
import com.skyzzz.guardian.api.util.RollingWindow;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reach on blocks: placing or breaking a block farther than the legal reach distance.
 * The break path reads the distance recorded by core because the break event carries no
 * eye position.
 */
public final class BlockReachCheck extends AbstractCheck {

    private final Map<UUID, RollingWindow> windows = new ConcurrentHashMap<>();

    public BlockReachCheck() {
        super("blockreach", CheckCategory.WORLD);
    }

    @Override
    public void onBlockPlace(PlayerProfile profile, BlockPlaceData place) {
        evaluate(profile, place.eyeDistance(), place.lineOfSight());
    }

    @Override
    public void onBlockBreak(PlayerProfile profile, BlockBreakData breakData) {
        Object distanceAttr = profile.attribute("last-break-eye-distance");
        double distance = distanceAttr instanceof Number n ? n.doubleValue() : -1.0D;
        if (distance < 0.0D) {
            return;
        }
        Object losAttr = profile.attribute("last-break-line-of-sight");
        evaluate(profile, distance, !Boolean.FALSE.equals(losAttr));
    }

    private void evaluate(PlayerProfile profile, double distance, boolean lineOfSight) {
        // Through-wall interactions at range belong to the aim/phase checks; reach only
        // measures distance with a clean line of sight.
        if (!lineOfSight) {
            return;
        }

        double max = scaled(profile, "max-reach", 4.5D) + d("buffer", 0.1D);
        double pingBonus = Math.min(d("ping-compensation-cap", 0.4D),
                profile.ping() * d("ping-compensation-per-ms", 0.0022D));
        double allowed = max + pingBonus;

        RollingWindow window = windows.computeIfAbsent(profile.uuid(),
                k -> new RollingWindow(i("window-size", 8)));

        if (distance <= allowed) {
            window.add(0.0D);
            reward(profile, d("clean-reward", 0.1D));
            return;
        }

        double excess = distance - allowed;
        window.add(excess);

        if (!window.isFull() || window.countAbove(d("minimum-excess", 0.05D)) < i("required-flags", 3)) {
            return;
        }

        flag(profile, Math.min(d("max-weight", 3.0D), excess / d("weight-per-block", 0.25D)),
                "block reach dist=%.3f allowed=%.3f ping=%dms",
                distance, allowed, profile.ping());
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        windows.remove(profile.uuid());
    }
}