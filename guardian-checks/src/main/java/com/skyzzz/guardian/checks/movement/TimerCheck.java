package com.skyzzz.guardian.checks.movement;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tick-rate manipulation. Maintains a balance: every movement packet adds
 * (50ms - actualInterval). A player running at normal speed keeps the balance near
 * zero; a timer cheat drives it steadily positive. Balance is clamped so a lag spike
 * cannot bank credit for later abuse.
 */
public final class TimerCheck extends AbstractCheck {

    private static final class State {
        long lastPacketNanos;
        double balance;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public TimerCheck() {
        super("timer", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMove(PlayerProfile profile, MoveData move) {
        State state = states.computeIfAbsent(profile.uuid(), key -> new State());

        if (state.lastPacketNanos == 0L) {
            state.lastPacketNanos = move.timestampNanos();
            return;
        }

        double intervalMs = (move.timestampNanos() - state.lastPacketNanos) / 1_000_000.0D;
        state.lastPacketNanos = move.timestampNanos();

        // Ignore the first packet after a teleport or a long stall.
        if (intervalMs <= 0.0D || intervalMs > d("ignore-above-ms", 250.0D)) {
            state.balance = 0.0D;
            return;
        }

        double tps = profile.attribute("server-tps") instanceof Number number
                ? number.doubleValue() : 20.0D;
        double expectedMs = 1000.0D / Math.max(1.0D, tps);

        state.balance += expectedMs - intervalMs;
        state.balance = Math.max(-d("max-negative-balance", 150.0D),
                Math.min(d("max-positive-balance", 250.0D), state.balance));

        double threshold = scaled(profile, "balance-threshold", 90.0D);
        if (state.balance > threshold) {
            flag(profile, 2.0D, "timer balance=%.2f interval=%.2fms expected=%.2fms",
                    state.balance, intervalMs, expectedMs);
            state.balance = 0.0D;
        }
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}