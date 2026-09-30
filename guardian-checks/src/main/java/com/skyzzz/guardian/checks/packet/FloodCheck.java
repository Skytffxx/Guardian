package com.skyzzz.guardian.checks.packet;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.PacketData;
import com.skyzzz.guardian.api.player.PlayerProfile;
import com.skyzzz.guardian.api.util.RollingWindow;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Packet flooding. Escalates from a soft flag to a hard VL; any kick comes from a
 * punishment tier in config.yml, not from this check. Packets are counted in real
 * one-second buckets so a burst straddling a boundary is not split.
 */
public final class FloodCheck extends AbstractCheck {

    private static final class State {
        final RollingWindow perSecond;
        long windowStartNanos;
        int packetsThisSecond;
        int hardViolations;

        State(int window) {
            this.perSecond = new RollingWindow(window);
        }
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public FloodCheck() {
        super("flood", CheckCategory.PACKET);
    }

    @Override
    public void onPacketReceive(PlayerProfile profile, PacketData packet) {
        State state = states.computeIfAbsent(profile.uuid(), key -> new State(i("window-seconds", 5)));

        if (state.windowStartNanos == 0L) {
            state.windowStartNanos = packet.timestampNanos();
        }

        state.packetsThisSecond++;

        double elapsedSeconds = (packet.timestampNanos() - state.windowStartNanos) / 1_000_000_000.0D;
        if (elapsedSeconds < 1.0D) {
            return;
        }

        int count = state.packetsThisSecond;
        state.windowStartNanos = packet.timestampNanos();
        state.packetsThisSecond = 0;
        state.perSecond.add(count);

        int softLimit = i("soft-limit-per-second", 400);
        int hardLimit = i("hard-limit-per-second", 900);

        if (count > hardLimit) {
            state.hardViolations++;
            flag(profile, 5.0D, "packet flood %d/s exceeds hard limit %d (violation %d)",
                    count, hardLimit, state.hardViolations);
            return;
        }
        if (count > softLimit && state.perSecond.isFull()
                && state.perSecond.countAbove(softLimit) >= i("soft-required-seconds", 3)) {
            flag(profile, 1.0D, "sustained packet rate %d/s over %d seconds (soft limit %d)",
                    count, state.perSecond.size(), softLimit);
            state.perSecond.clear();
        }
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}