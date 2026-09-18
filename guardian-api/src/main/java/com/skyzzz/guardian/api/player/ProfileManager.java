package com.skyzzz.guardian.api.player;

import java.util.Collection;
import java.util.UUID;

public interface ProfileManager {

    PlayerProfile get(UUID uuid);

    Collection<PlayerProfile> online();

    void remove(UUID uuid);

    void tickAll();

    /** Total VL across every check, used for trust scores / PlaceholderAPI. */
    double totalViolations(UUID uuid);
}