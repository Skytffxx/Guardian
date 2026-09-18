package com.skyzzz.guardian.checks.combat;

import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.player.PlayerProfile;

/**
 * Knockback manipulation.
 *
 * Core records every outbound velocity packet into the profile. This check compares
 * the horizontal delta the client actually applied over the following ticks against
 * the delta the server sent. A ratio consistently below 1.0 (typical "velocity"
 * cheat) triggers a flag; a single low ratio is ignored because lag and block
 * collisions legitimately absorb knockback.
 *
 * TODO(next pass): wire the outbound velocity samples into PlayerProfile so this
 * class can read them directly instead of relying on core-side pre-filtering.
 */
public final class VelocityCheck extends AbstractCheck {

    public VelocityCheck() {
        super("velocity", CheckCategory.COMBAT);
    }

    @Override
    public void onMove(PlayerProfile profile, MoveData move) {
        // Skeleton — deliberately inert until the velocity sample feed lands.
        // Never ship an inert check as enabled: config ships this at enabled: false.
        reward(profile, d("clean-reward", 0.1D));
    }
}