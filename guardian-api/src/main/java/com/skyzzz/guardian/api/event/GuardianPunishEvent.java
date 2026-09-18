package com.skyzzz.guardian.api.event;

import java.util.List;

import com.skyzzz.guardian.api.player.PlayerProfile;

public final class GuardianPunishEvent {

    private final PlayerProfile profile;
    private final String source;
    private final double violationLevel;
    private final List<String> commands;

    public GuardianPunishEvent(PlayerProfile profile, String source,
                               double violationLevel, List<String> commands) {
        this.profile = profile;
        this.source = source;
        this.violationLevel = violationLevel;
        this.commands = commands;
    }

    public PlayerProfile profile() {
        return profile;
    }

    public String source() {
        return source;
    }

    public double violationLevel() {
        return violationLevel;
    }

    public List<String> commands() {
        return commands;
    }
}