package com.skyzzz.guardian.checks.world;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.BlockBreakData;
import com.skyzzz.guardian.api.player.PlayerProfile;

/**
 * FastBreak: blocks breaking faster than the server-side hardness calculation allows.
 * TODO(next pass): needs block hardness + tool efficiency + haste/conduit power from
 * core so the expected break time can be computed server-side and compared.
 */
public final class FastBreakCheck extends AbstractCheck {

    public FastBreakCheck() {
        super("fastbreak", CheckCategory.WORLD);
    }

    @Override
    public void onBlockBreak(PlayerProfile profile, BlockBreakData breakData) {
        // Skeleton.
    }
}