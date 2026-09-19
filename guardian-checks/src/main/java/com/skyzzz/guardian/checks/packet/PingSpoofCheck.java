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
 * Ping / KeepAlive spoofing.
 *
 * Core stores the outbound keep-alive id and send timestamp on the profile. This
 * check validates the client's response:
 *   - the echoed id must match the last sent id,
 *   - the round-trip time must be plausible for the player's declared ping,
 *   - responses must not be duplicated.
 *
 * A single out-of-order response is normal on lossy networks; require a small window
 * of wrong responses before flagging.
 */
public final class PingSpoofCheck extends AbstractCheck {

    private static final class State {
        long lastSentId = Long.MIN_VALUE;
        long lastSentNanos;
        long lastResponseId = Long.MIN_VALUE;
        int wrongIdStreak;
        final RollingWindow roundTripMs;

        State(int windowSize) {
            this.roundTripMs = new RollingWindow(windowSize);
        }
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public PingSpoofCheck() {
        super("pingspoof", CheckCategory.PACKET);
    }

    @Override
    public void onPacketSend(PlayerProfile profile, PacketData packet) {
        if (!packet.matchesName("KeepAlive")) {
            return;
        }
        State state = states.computeIfAbsent(profile.uuid(),
                k -> new State(i("window-size", 10)));

        Object idAttr = profile.attribute("keepalive-sent-id");
        Object timeAttr = profile.attribute("keepalive-sent-nanos");
        if (idAttr instanceof Long id) {
            state.lastSentId = id;
        }
        if (timeAttr instanceof Long t) {
            state.lastSentNanos = t;
        }
    }

    @Override
    public void onPacketReceive(PlayerProfile profile, PacketData packet) {
        if (!packet.matchesName("KeepAlive")) {
            return;
        }
        State state = states.computeIfAbsent(profile.uuid(),
                k -> new State(i("window-size", 10)));

        Object idAttr = profile.attribute("keepalive-response-id");
        Object timeAttr = profile.attribute("keepalive-response-nanos");
        if (!(idAttr instanceof Long id) || !(timeAttr instanceof Long responseNanos)) {
            return;
        }

        // Duplicate response for the same id — the client replayed a stale packet.
        if (id == state.lastResponseId) {
            flag(profile, 2.0D, "duplicate keepalive response id=%d", id);
            return;
        }

        // Id mismatch — the client responded to a keep-alive we never sent.
        if (state.lastSentId != Long.MIN_VALUE && id != state.lastSentId) {
            state.wrongIdStreak++;
            if (state.wrongIdStreak >= i("wrong-id-streak", 3)) {
                flag(profile, 2.0D,
                        "keepalive id mismatch sent=%d received=%d (streak=%d)",
                        state.lastSentId, id, state.wrongIdStreak);
                state.wrongIdStreak = 0;
            }
            return;
        } else {
            state.wrongIdStreak = Math.max(0, state.wrongIdStreak - 1);
        }

        // Round-trip sanity check.
        if (state.lastSentNanos != 0L) {
            double rttMs = (responseNanos - state.lastSentNanos) / 1_000_000.0D;
            if (rttMs >= 0.0D && rttMs < 10_000.0D) {
                state.roundTripMs.add(rttMs);

                if (state.roundTripMs.isFull()) {
                    double meanRtt = state.roundTripMs.mean();
                    int declaredPing = profile.ping();
                    // If the server-measured RTT is drastically lower than the client's
                    // own declared ping, the client is overstating its latency.
                    if (declaredPing > 0 && meanRtt * d("ping-mismatch-factor", 3.0D) < declaredPing) {
                        flag(profile, 1.0D,
                                "keepalive rtt=%.1fms vs declared ping=%dms",
                                meanRtt, declaredPing);
                        state.roundTripMs.clear();
                    }
                }
            }
        }

        state.lastResponseId = id;
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}