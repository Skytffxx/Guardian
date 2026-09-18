package com.skyzzz.guardian.checks.movement;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.player.PlayerProfile;

/**
 * Phase / NoClip. Detection needs chunk-accurate collision resolution:
 * a movement packet that ends inside a full collision box the player could not
 * have entered. TODO(next pass): implement AABB sweep against
 * {@code profile.attribute("nearby-collision-boxes")}.
 */
public final class PhaseCheck extends AbstractCheck {

    public PhaseCheck() {
        super("phase", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMove(PlayerProfile profile, MoveData move) {
        // Skeleton.
    }
}