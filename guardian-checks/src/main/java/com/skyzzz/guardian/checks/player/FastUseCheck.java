package com.skyzzz.guardian.checks.player;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.PacketData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FastUse: completing an eat/drink faster than the vanilla use duration.
 *
 * Core feeds us a packet named {@code USE_ITEM_COMPLETED} with the use duration in
 * {@code profile.attribute("last-use-duration-ms")}. We compare against the item's
 * legal minimum, which core resolves from the held item type.
 */
public final class FastUseCheck extends AbstractCheck {

    private static final class State {
        int streak;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public FastUseCheck() {
        super("fastuse", CheckCategory.PLAYER);
    }

    @Override
    public void onPacketReceive(PlayerProfile profile, PacketData packet) {
        if (!packet.packetName().contains("UseItem")) {
            return;
        }
        Object durationAttr = profile.attribute("last-use-duration-ms");
        if (!(durationAttr instanceof Number durationNumber)) {
            return;
        }

        State state = states.computeIfAbsent(profile.uuid(), key -> new State());
        double durationMs = durationNumber.doubleValue();
        double expectedMs = scaled(profile, "expected-duration-ms", 1610.0D);
        double tolerance = d("tolerance-ms", 120.0D);

        if (durationMs < expectedMs - tolerance) {
            state.streak++;
        } else {
            state.streak = Math.max(0, state.streak - 1);
        }

        if (state.streak >= i("required-flags", 2)) {
            flag(profile, 1.0D, "use duration=%.0fms expected=%.0fms streak=%d",
                    durationMs, expectedMs, state.streak);
            state.streak = 0;
        }
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}