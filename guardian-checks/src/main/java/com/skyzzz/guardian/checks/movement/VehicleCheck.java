package com.skyzzz.guardian.checks.movement;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vehicle exploits (boat fly, minecart and horse speed). Compares per-tick movement
 * while riding against the maximum the vehicle legally allows, using the server-side
 * vehicle position rather than the client's own report.
 */
public final class VehicleCheck extends AbstractCheck {

    private static final class State {
        double lastVehicleX = Double.NaN;
        double lastVehicleZ = Double.NaN;
        int speedStreak;
        int airStreak;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public VehicleCheck() {
        super("vehicle", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMove(PlayerProfile profile, MoveData move) {
        State state = states.computeIfAbsent(profile.uuid(), k -> new State());

        if (!Boolean.TRUE.equals(profile.attribute("vehicle"))) {
            state.lastVehicleX = Double.NaN;
            state.lastVehicleZ = Double.NaN;
            state.speedStreak = 0;
            state.airStreak = 0;
            return;
        }

        double vx = attr(profile, "vehicle-x", 0.0D);
        double vz = attr(profile, "vehicle-z", 0.0D);

        if (Double.isNaN(state.lastVehicleX)) {
            state.lastVehicleX = vx;
            state.lastVehicleZ = vz;
            return;
        }

        double dx = vx - state.lastVehicleX;
        double dz = vz - state.lastVehicleZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        state.lastVehicleX = vx;
        state.lastVehicleZ = vz;

        if (horizontal > scaled(profile, "max-vehicle-speed", 0.9D)) {
            state.speedStreak++;
        } else {
            state.speedStreak = Math.max(0, state.speedStreak - 1);
        }

        if (move.deltaY() > d("max-vehicle-rise", 0.05D)) {
            state.airStreak++;
        } else {
            state.airStreak = Math.max(0, state.airStreak - 1);
        }

        if (state.speedStreak >= i("speed-streak", 5)) {
            flag(profile, 2.0D, "vehicle speed %.4f exceeds %.4f (streak=%d)",
                    horizontal, scaled(profile, "max-vehicle-speed", 0.9D), state.speedStreak);
            state.speedStreak = 0;
            return;
        }

        if (state.airStreak >= i("air-streak", 8)) {
            flag(profile, 2.5D, "vehicle rising illegally deltaY=%.4f streak=%d",
                    move.deltaY(), state.airStreak);
            state.airStreak = 0;
        }
    }

    private double attr(PlayerProfile profile, String key, double def) {
        Object value = profile.attribute(key);
        return value instanceof Number n ? n.doubleValue() : def;
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}