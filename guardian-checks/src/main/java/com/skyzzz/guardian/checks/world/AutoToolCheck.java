package com.skyzzz.guardian.checks.world;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.BlockBreakData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AutoTool.
 *
 * A cheat switches to the optimal hotbar slot in the same tick the block break
 * begins, with zero human reaction time — repeatedly. Detection: on a block break,
 * if the last hotbar switch happened within the last N ticks AND the switch moved
 * to a tool matching the block being broken, streak. Legitimate players do switch
 * quickly; the pattern that survives is the sustained zero-reaction one.
 */
public final class AutoToolCheck extends AbstractCheck {

    private static final class State {
        int streak;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public AutoToolCheck() {
        super("autotool", CheckCategory.WORLD);
    }

    @Override
    public void onBlockBreak(PlayerProfile profile, BlockBreakData breakData) {
        State state = states.computeIfAbsent(profile.uuid(), k -> new State());

        Object switchAttr = profile.attribute("last-item-switch-tick");
        if (!(switchAttr instanceof Long switchTick)) {
            state.streak = Math.max(0, state.streak - 1);
            return;
        }

        long ticksSinceSwitch = profile.tick() - switchTick;

        int reactionWindow = i("reaction-window-ticks", 2);
        String tool = (String) profile.attribute("tool-type");
        boolean optimalTool = tool != null && isOptimalFor(tool, breakData.material());

        if (ticksSinceSwitch <= reactionWindow && optimalTool) {
            state.streak++;
        } else {
            state.streak = Math.max(0, state.streak - 1);
        }

        if (state.streak >= i("required-flags", 6)) {
            flag(profile, 1.5D,
                    "auto-tool streak=%d switch %d ticks before breaking %s with %s",
                    state.streak, ticksSinceSwitch, breakData.material(), tool);
            state.streak = 0;
        }
    }

    private boolean isOptimalFor(String tool, String material) {
        if (material.contains("STONE") || material.contains("ORE") || material.contains("DEEPSLATE")) {
            return tool.endsWith("_PICKAXE");
        }
        if (material.contains("LOG") || material.contains("PLANKS") || material.contains("WOOD")) {
            return tool.endsWith("_AXE");
        }
        if (material.contains("DIRT") || material.contains("SAND") || material.contains("GRAVEL")) {
            return tool.endsWith("_SHOVEL");
        }
        return false;
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}