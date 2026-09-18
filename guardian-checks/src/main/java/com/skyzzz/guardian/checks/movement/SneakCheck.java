package com.skyzzz.guardian.checks.movement;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.player.PlayerProfile;

/**
 * Pose exploits (crawling/sneaking to shrink the hitbox when the server disagrees).
 * TODO(next pass): compare client pose packets against server pose state.
 */
public final class SneakCheck extends AbstractCheck {

    public SneakCheck() {
        super("sneak", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMove(PlayerProfile profile, MoveData move) {
        // Skeleton.
    }
}