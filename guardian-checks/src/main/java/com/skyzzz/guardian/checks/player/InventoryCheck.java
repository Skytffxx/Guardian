package com.skyzzz.guardian.checks.player;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.PacketData;
import com.skyzzz.guardian.api.player.PlayerProfile;
import com.skyzzz.guardian.api.util.RollingWindow;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inventory click-sequence exploits.
 *
 * Now that core feeds {@code last-click-slot} and {@code last-click-window-id} from
 * the raw click packet, this check has real slot data to work with. Two signals:
 *
 *   1. Click rate — sustained above the human ceiling.
 *   2. Same-slot spam — clicking the same slot repeatedly at machine cadence,
 *      which is what inventory-move macros produce (the client's own click queue
 *      can only hold a handful of pending clicks, so a sustained same-slot burst
 *      cannot be legitimate input).
 *
 * Both must agree before VL moves. Either alone is common.
 */
public final class InventoryCheck extends AbstractCheck {

    private static final class State {
        long lastClickNanos;
        int lastSlot = Integer.MIN_VALUE;
        int lastWindowId = Integer.MIN_VALUE;
        int sameSlotStreak;
        final RollingWindow clickRatePerSecond;

        State(int windowSeconds) {
            this.clickRatePerSecond = new RollingWindow(windowSeconds);
        }
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public InventoryCheck() {
        super("inventory", CheckCategory.PLAYER);
    }

    @Override
    public void onPacketReceive(PlayerProfile profile, PacketData packet) {
        if (!packet.packetName().contains("ClickWindow")) {
            return;
        }

        State state = states.computeIfAbsent(profile.uuid(),
                k -> new State(i("window-seconds", 5)));

        long now = packet.timestampNanos();

        // --- signal 1: click rate ---
        if (state.lastClickNanos != 0L) {
            double intervalMs = (now - state.lastClickNanos) / 1_000_000.0D;
            if (intervalMs > 0.0D && intervalMs < d("ignore-above-ms", 500.0D)) {
                state.clickRatePerSecond.add(1000.0D / intervalMs);
            }
        }
        state.lastClickNanos = now;

        // --- signal 2: same-slot streak ---
        Object slotAttr = profile.attribute("last-click-slot");
        Object windowAttr = profile.attribute("last-click-window-id");
        int slot = slotAttr instanceof Number n ? n.intValue() : Integer.MIN_VALUE;
        int windowId = windowAttr instanceof Number n ? n.intValue() : Integer.MIN_VALUE;

        if (slot == state.lastSlot && windowId == state.lastWindowId
                && slot != Integer.MIN_VALUE) {
            state.sameSlotStreak++;
        } else {
            state.sameSlotStreak = Math.max(0, state.sameSlotStreak - 3);
        }
        state.lastSlot = slot;
        state.lastWindowId = windowId;

        // --- evaluate ---
        if (!state.clickRatePerSecond.isFull()) {
            return;
        }

        double meanCps = state.clickRatePerSecond.mean();
        double maxCps = scaled(profile, "maximum-cps", 25.0D);
        boolean rateExceeded = meanCps > maxCps;
        boolean sameSlotSpam = state.sameSlotStreak >= i("same-slot-streak", 8);

        if (rateExceeded && sameSlotSpam) {
            flag(profile, 2.0D,
                    "inventory clicks cps=%.1f (max %.1f) same-slot=%d (slot=%d window=%d)",
                    meanCps, maxCps, state.sameSlotStreak, slot, windowId);
            state.clickRatePerSecond.clear();
            state.sameSlotStreak = 0;
        }
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}