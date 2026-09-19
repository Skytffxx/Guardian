package com.skyzzz.guardian.checks.player;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.PacketData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AutoRespawn: respawning faster than a human can react after death.
 * TODO(next pass): needs the death timestamp from core's death listener.
 */
public final class AutoRespawnCheck extends AbstractCheck {

    private final Map<UUID, Long> deathTimes = new ConcurrentHashMap<>();

    public AutoRespawnCheck() {
        super("autorespawn", CheckCategory.PLAYER);
    }

    /** Called by core on PlayerDeathEvent. */
    public void onDeath(UUID uuid, long timestampNanos) {
        deathTimes.put(uuid, timestampNanos);
    }

    @Override
    public void onPacketReceive(PlayerProfile profile, PacketData packet) {
        if (!packet.matchesName("ClientStatus")) {
            return;
        }
        Long deathTime = deathTimes.remove(profile.uuid());
        if (deathTime == null) {
            return;
        }
        double reactionMs = (packet.timestampNanos() - deathTime) / 1_000_000.0D;
        if (reactionMs < scaled(profile, "minimum-reaction-ms", 150.0D)) {
            flag(profile, 1.0D, "respawn reaction=%.0fms", reactionMs);
        }
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        deathTimes.remove(profile.uuid());
    }
}