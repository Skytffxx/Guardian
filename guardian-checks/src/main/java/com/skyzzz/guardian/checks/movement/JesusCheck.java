package com.skyzzz.guardian.checks.movement;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.player.PlayerProfile;

/**
 * Water-walk. Requires core-supplied block material at the player's feet.
 * TODO(next pass): read {@code profile.attribute("block-at-feet")} and
 * {@code "block-below-feet"} to detect standing on a liquid surface.
 */
public final class JesusCheck extends AbstractCheck {

    public JesusCheck() {
        super("jesus", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMove(PlayerProfile profile, MoveData move) {
        // Skeleton.
    }
}