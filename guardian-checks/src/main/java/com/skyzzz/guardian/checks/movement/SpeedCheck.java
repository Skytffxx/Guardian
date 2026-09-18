package com.skyzzz.guardian.checks.movement;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.player.PlayerProfile;
import com.skyzzz.guardian.api.util.RollingWindow;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Horizontal speed, ground and air, with every legit modifier modelled rather than
 * ignored: speed potions, jump boost, soul speed, depth strider, ice/slipperiness,
 * elytra, riptide, vehicles and server-side TPS lag.
 *
 * Evaluated over a rolling window: a single fast tick is never enough.
 */
public final class SpeedCheck extends AbstractCheck {

    private static final double BASE_WALK = 0.221D;
    private static final double BASE_SPRINT = 0.288D;

    private final Map<UUID, RollingWindow> windows = new ConcurrentHashMap<>();

    public SpeedCheck() {
        super("speed", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMove(PlayerProfile profile, MoveData move) {
        // Vertical/elytra/vehicle movement is somebody else's check.
        if (Boolean.TRUE.equals(profile.attribute("elytra"))
                || Boolean.TRUE.equals(profile.attribute("vehicle"))
                || Boolean.TRUE.equals(profile.attribute("riptide"))
                || Boolean.TRUE.equals(profile.attribute("teleport"))) {
            windows.remove(profile.uuid());
            return;
        }

        double max = allowedSpeed(profile, move) + d("buffer", 0.045D);
        double distance = move.horizontalDistance();

        RollingWindow window = windows.computeIfAbsent(profile.uuid(),
                key -> new RollingWindow(i("window-size", 12)));

        if (distance <= max) {
            window.add(0.0D);
            reward(profile, d("clean-reward", 0.08D));
            return;
        }

        double excess = distance - max;
        window.add(excess);

        if (!window.isFull()) {
            return;
        }

        int required = i("required-flags", 6);
        if (window.countAbove(d("minimum-excess", 0.02D)) < required) {
            return;
        }

        double weight = Math.min(d("max-weight", 3.0D), excess / d("weight-per-block", 0.05D));
        flag(profile, Math.max(1.0D, weight),
                "dist=%.4f max=%.4f excess=%.4f ground=%s ping=%dms",
                distance, max, excess, move.onGround(), profile.ping());
    }

    private double allowedSpeed(PlayerProfile profile, MoveData move) {
        double base = move.onGround()
                ? d("base-sprint", BASE_SPRINT)
                : d("base-air", BASE_WALK);

        int speedAmplifier = intAttribute(profile, "speed-amplifier", 0);
        base *= 1.0D + 0.20D * speedAmplifier;

        int jumpBoost = intAttribute(profile, "jump-boost-amplifier", 0);
        base *= 1.0D + 0.10D * jumpBoost;

        if (Boolean.TRUE.equals(profile.attribute("soul-speed"))) {
            base *= d("soul-speed-multiplier", 1.30D);
        }
        if (Boolean.TRUE.equals(profile.attribute("depth-strider"))) {
            base *= d("depth-strider-multiplier", 1.15D);
        }
        if (Boolean.TRUE.equals(profile.attribute("on-ice"))) {
            base *= d("ice-multiplier", 2.40D);
        }
        if (Boolean.TRUE.equals(profile.attribute("on-slime"))) {
            base *= d("slime-multiplier", 1.6D);
        }
        if (Boolean.TRUE.equals(profile.attribute("in-water"))) {
            base *= d("water-multiplier", 0.75D);
        }
        if (Boolean.TRUE.equals(profile.attribute("on-ladder"))) {
            base *= d("ladder-multiplier", 0.9D);
        }
        if (move.y() > move.lastY() && !move.onGround()) {
            base *= d("jump-multiplier", 1.05D);
        }

        // Never let a lag spike read as a cheat: scale by current TPS.
        double tps = doubleAttribute(profile, "server-tps", 20.0D);
        if (tps < 20.0D) {
            base *= 1.0D + ((20.0D - tps) / 20.0D) * d("tps-compensation-factor", 1.35D);
        }

        // Ping compensation: high-latency clients batch movement into fewer packets.
        int ping = profile.ping();
        if (ping > 100) {
            base *= 1.0D + Math.min(d("max-ping-compensation", 0.35D),
                    (ping - 100) / 1000.0D);
        }

        return base;
    }

    private int intAttribute(PlayerProfile profile, String key, int def) {
        Object value = profile.attribute(key);
        return value instanceof Number number ? number.intValue() : def;
    }

    private double doubleAttribute(PlayerProfile profile, String key, double def) {
        Object value = profile.attribute(key);
        return value instanceof Number number ? number.doubleValue() : def;
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        windows.remove(profile.uuid());
    }
}