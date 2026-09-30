package com.skyzzz.guardian.checks.world;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.BlockBreakData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AutoTool: the client switches to the optimal hotbar slot in the same tick a break
 * starts, with no human reaction time, over and over. We streak breaks whose preceding
 * switch happened within a couple of ticks and matched the block, so an occasional fast
 * switch never flags.
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

        long sinceSwitch = profile.tick() - switchTick;
        String tool = (String) profile.attribute("tool-type");
        boolean optimal = tool != null && optimalFor(tool, breakData.material());

        if (sinceSwitch <= i("reaction-window-ticks", 2) && optimal) {
            state.streak++;
        } else {
            state.streak = Math.max(0, state.streak - 1);
        }

        int required = i("required-flags", 6);
        if (state.streak >= required) {
            flag(profile, 1.5D, "auto-tool streak=%d switch %d ticks before breaking %s with %s",
                    state.streak, sinceSwitch, breakData.material(), tool);
            state.streak = 0;
        }
    }

    private boolean optimalFor(String tool, String material) {
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