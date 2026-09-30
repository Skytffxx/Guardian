package com.skyzzz.guardian.api.player;

import java.util.Collection;
import java.util.UUID;

public interface ProfileManager {

    PlayerProfile get(UUID uuid);

    Collection<PlayerProfile> online();

    void remove(UUID uuid);

    void tickAll();

    /** Sum of every check's VL for this player. */
    double totalViolations(UUID uuid);
}