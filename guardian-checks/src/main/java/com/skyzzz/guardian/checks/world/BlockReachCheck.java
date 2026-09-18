package com.skyzzz.guardian.checks.world;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.BlockPlaceData;
import com.skyzzz.guardian.api.player.PlayerProfile;

/**
 * Reach-on-blocks: interacting with a block farther than the legal reach distance.
 * Mirrors ReachCheck but for BlockPlaceData / BlockBreakData.
 */
public final class BlockReachCheck extends AbstractCheck {

    public BlockReachCheck() {
        super("blockreach", CheckCategory.WORLD);
    }

    @Override
    public void onBlockPlace(PlayerProfile profile, BlockPlaceData place) {
        double max = scaled(profile, "max-reach", 4.5D) + d("buffer", 0.1D);
        double pingBonus = Math.min(d("ping-compensation-cap", 0.4D),
                profile.ping() * d("ping-compensation-per-ms", 0.0022D));
        double allowed = max + pingBonus;

        if (place.eyeDistance() > allowed) {
            flag(profile, Math.min(d("max-weight", 3.0D),
                            (place.eyeDistance() - allowed) / d("weight-per-block", 0.25D)),
                    "block reach dist=%.3f allowed=%.3f ping=%dms",
                    place.eyeDistance(), allowed, profile.ping());
        }
    }
}