package com.skyzzz.guardian.checks.combat;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.AttackData;
import com.skyzzz.guardian.api.player.PlayerProfile;

/**
 * Fake criticals: a critical hit is claimed while the server knows the attacker was
 * on the ground, or the attacker's fall state cannot produce a crit.
 *
 * TODO(next pass): AttackData needs {@code boolean criticalClaimed} and
 * {@code boolean serverFallState} supplied by core from the swing packet and the
 * server-side fall distance.
 */
public final class FakeCriticalsCheck extends AbstractCheck {

    public FakeCriticalsCheck() {
        super("fakecriticals", CheckCategory.COMBAT);
    }

    @Override
    public void onAttack(PlayerProfile profile, AttackData attack) {
        // Skeleton — see TODO above.
    }
}