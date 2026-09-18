package com.skyzzz.guardian.checks.player;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.PacketData;
import com.skyzzz.guardian.api.player.PlayerProfile;

/**
 * Inventory click-sequence exploits: impossible slot transitions, clicks outside the
 * window bounds, and click rates that exceed the human ceiling for extended periods.
 *
 * TODO(next pass): needs window-id and slot tracking from the click-window packet.
 */
public final class InventoryCheck extends AbstractCheck {

    public InventoryCheck() {
        super("inventory", CheckCategory.PLAYER);
    }

    @Override
    public void onPacketReceive(PlayerProfile profile, PacketData packet) {
        // Skeleton.
    }
}