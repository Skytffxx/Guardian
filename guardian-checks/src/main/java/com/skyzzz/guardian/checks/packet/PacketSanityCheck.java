package com.skyzzz.guardian.checks.packet;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.data.PacketData;
import com.skyzzz.guardian.api.player.PlayerProfile;

/**
 * Core packet sanity. This is deliberately part of the hardcoded 10–15%: it is
 * NOT disableable via config, because a careless config edit here turns directly
 * into a bypass. {@link #bind} ignores the enabled flag for this check.
 */
public final class PacketSanityCheck extends AbstractCheck {

    public PacketSanityCheck() {
        super("packetsanity", CheckCategory.PACKET);
    }

    @Override
    public void bind(com.skyzzz.guardian.api.check.CheckSettings settings) {
        super.bind(settings);
        // Sanity validation is structural, not tunable — always active.
        setEnabled(true);
    }

    @Override
    public void onMove(PlayerProfile profile, MoveData move) {
        if (!isFinite(move.x()) || !isFinite(move.y()) || !isFinite(move.z())
                || !isFinite(move.yaw()) || !isFinite(move.pitch())) {
            flag(profile, 10.0D, "non-finite position x=%s y=%s z=%s yaw=%s pitch=%s",
                    move.x(), move.y(), move.z(), move.yaw(), move.pitch());
            profile.setAttribute("teleport", Boolean.TRUE);
            return;
        }

        double ceiling = d("coordinate-ceiling", 3.0E7D);
        if (Math.abs(move.x()) > ceiling || Math.abs(move.z()) > ceiling
                || Math.abs(move.y()) > ceiling) {
            flag(profile, 10.0D, "position outside world bounds x=%.1f y=%.1f z=%.1f",
                    move.x(), move.y(), move.z());
        }

        if (move.pitch() < -90.5F || move.pitch() > 90.5F) {
            flag(profile, 5.0D, "invalid pitch %.3f", move.pitch());
        }

        // A single tick cannot legally move a player more than the chunk border.
        double maxTickDistance = d("max-tick-distance", 100.0D);
        if (move.threeDimensionalDistance() > maxTickDistance) {
            flag(profile, 5.0D, "tick movement %.2f exceeds %.2f",
                    move.threeDimensionalDistance(), maxTickDistance);
        }
    }

    @Override
    public void onPacketReceive(PlayerProfile profile, PacketData packet) {
        if (packet.packet() == null) {
            flag(profile, 5.0D, "null packet body for %s", packet.packetName());
        }
    }

    private boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}