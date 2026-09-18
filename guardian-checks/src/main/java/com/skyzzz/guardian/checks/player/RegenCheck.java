package com.skyzzz.guardian.checks.player;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.player.PlayerProfile;

/**
 * Regeneration / HealthBoost anomalies: server-side health regen exceeding the
 * expected cadence for the player's active effects.
 *
 * TODO(next pass): needs {@code profile.attribute("health")} history from core and
 * the active effect amplifier.
 */
public final class RegenCheck extends AbstractCheck {

    public RegenCheck() {
        super("regen", CheckCategory.PLAYER);
    }

    @Override
    public void onTick(PlayerProfile profile) {
        // Skeleton.
    }
}