package com.skyzzz.guardian.checks.movement;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.player.PlayerProfile;

/**
 * Elytra-specific: illegal mid-air boost, firework-abuse timing, no-glide fall.
 * TODO(next pass): needs {@code profile.attribute("gliding")} plus firework
 * boost timestamps from the entity metadata listener.
 */
public final class ElytraCheck extends AbstractCheck {

    public ElytraCheck() {
        super("elytra", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMove(PlayerProfile profile, MoveData move) {
        // Skeleton.
    }
}