package com.skyzzz.guardian.checks.movement;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.player.PlayerProfile;

/**
 * Vehicle-based movement cheats: boat fly, minecart speed, horse speed hacks.
 * TODO(next pass): needs the ridden entity's server-side position from core.
 */
public final class VehicleCheck extends AbstractCheck {

    public VehicleCheck() {
        super("vehicle", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMove(PlayerProfile profile, MoveData move) {
        // Skeleton.
    }
}