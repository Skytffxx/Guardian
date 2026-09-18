package com.skyzzz.guardian.checks.world;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.player.PlayerProfile;
import com.skyzzz.guardian.api.data.BlockBreakData;

/**
 * AutoTool: switching to the optimal hotbar slot in the same tick the break starts,
 * with zero human reaction time, repeatedly.
 * TODO(next pass): needs hotbar-switch timestamps from the held-item packet.
 */
public final class AutoToolCheck extends AbstractCheck {

    public AutoToolCheck() {
        super("autotool", CheckCategory.WORLD);
    }

    @Override
    public void onBlockBreak(PlayerProfile profile, BlockBreakData breakData) {
        // Skeleton.
    }
}