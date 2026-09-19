package com.skyzzz.guardian.checks.movement;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Water-walk. Detects standing on the surface of water or lava without sinking.
 *
 * Legitimate survival: the player's feet block is water, and they are actively
 * swimming (deltaY != 0 or in water movement mode). Cheat: feet block is water,
 * vertical delta stays near zero, and the player is not on a solid block.
 */
public final class JesusCheck extends AbstractCheck {

    private static final class State {
        int hoverStreak;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public JesusCheck() {
        super("jesus", CheckCategory.MOVEMENT);
    }

    @Override
    public void onMove(PlayerProfile profile, MoveData move) {
        State state = states.computeIfAbsent(profile.uuid(), k -> new State());

        if (Boolean.TRUE.equals(profile.attribute("teleport"))
                || Boolean.TRUE.equals(profile.attribute("vehicle"))
                || Boolean.TRUE.equals(profile.attribute("elytra"))
                || Boolean.TRUE.equals(profile.attribute("on-climbable"))) {
            state.hoverStreak = 0;
            return;
        }

        String atFeet = (String) profile.attribute("block-at-feet-type");
        String belowFeet = (String) profile.attribute("block-below-type");

        boolean overLiquid = isLiquid(atFeet) || isLiquid(belowFeet);
        if (!overLiquid) {
            state.hoverStreak = Math.max(0, state.hoverStreak - 2);
            return;
        }

        // Vertical motion means they are actually swimming, not walking.
        double vertical = Math.abs(move.deltaY());
        boolean hovering = vertical < d("max-hover-delta-y", 0.02D)
                && move.horizontalDistance() > d("minimum-horizontal", 0.05D);

        if (hovering) {
            state.hoverStreak++;
        } else {
            state.hoverStreak = Math.max(0, state.hoverStreak - 1);
        }

        if (state.hoverStreak >= i("required-flags", 10)) {
            flag(profile, 2.0D,
                    "standing on liquid surface streak=%d at=%s below=%s",
                    state.hoverStreak, atFeet, belowFeet);
            state.hoverStreak = 0;
        }
    }

    private boolean isLiquid(String material) {
        if (material == null) {
            return false;
        }
        return material.equals("WATER") || material.equals("LAVA")
                || material.equals("BUBBLE_COLUMN");
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}