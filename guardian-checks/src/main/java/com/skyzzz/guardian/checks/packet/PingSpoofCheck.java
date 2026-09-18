package com.skyzzz.guardian.checks.packet;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.PacketData;
import com.skyzzz.guardian.api.player.PlayerProfile;

/**
 * Ping / KeepAlive spoofing: the client responds to a keep-alive with the wrong id,
 * responds twice, or responds implausibly fast for its declared latency.
 *
 * TODO(next pass): needs the keep-alive id→send-timestamp map from core so this
 * check can validate the echoed id and the round-trip time.
 */
public final class PingSpoofCheck extends AbstractCheck {

    public PingSpoofCheck() {
        super("pingspoof", CheckCategory.PACKET);
    }

    @Override
    public void onPacketReceive(PlayerProfile profile, PacketData packet) {
        // Skeleton.
    }
}