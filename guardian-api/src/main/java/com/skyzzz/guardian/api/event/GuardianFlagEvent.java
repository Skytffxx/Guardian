package com.skyzzz.guardian.api.event;

import com.skyzzz.guardian.api.check.Check;
import com.skyzzz.guardian.api.player.PlayerProfile;

/**
 * Framework-agnostic flag event. Core mirrors it into a Bukkit event when a
 * listener needs to cancel or annotate it.
 */
public final class GuardianFlagEvent {

    private final PlayerProfile profile;
    private final Check check;
    private final double weight;
    private final double newViolationLevel;
    private final String debug;
    private boolean cancelled;

    public GuardianFlagEvent(PlayerProfile profile, Check check, double weight,
                             double newViolationLevel, String debug) {
        this.profile = profile;
        this.check = check;
        this.weight = weight;
        this.newViolationLevel = newViolationLevel;
        this.debug = debug;
    }

    public PlayerProfile profile() {
        return profile;
    }

    public Check check() {
        return check;
    }

    public double weight() {
        return weight;
    }

    public double newViolationLevel() {
        return newViolationLevel;
    }

    public String debug() {
        return debug;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}