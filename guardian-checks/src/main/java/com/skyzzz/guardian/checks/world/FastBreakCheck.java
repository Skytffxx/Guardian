package com.skyzzz.guardian.checks.world;

import com.skyzzz.guardian.api.check.AbstractCheck;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.data.BlockBreakData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FastBreak, compared against the real vanilla break-time formula:
 * {@code damagePerTick = toolSpeed / hardness / (canHarvest ? 30 : 100)}.
 * The streak requirement exists because the first break of a block type has no prior
 * sample and jitter can make a single interval look short.
 */
public final class FastBreakCheck extends AbstractCheck {

    private static final class State {
        String lastMaterial;
        long lastBreakNanos;
        int streak;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public FastBreakCheck() {
        super("fastbreak", CheckCategory.WORLD);
    }

    @Override
    public void onBlockBreak(PlayerProfile profile, BlockBreakData breakData) {
        State state = states.computeIfAbsent(profile.uuid(), k -> new State());

        if (breakData.instantBreak()) {
            state.lastMaterial = null;
            state.streak = 0;
            return;
        }

        long now = breakData.timestampNanos();
        if (state.lastBreakNanos == 0L || !breakData.material().equals(state.lastMaterial)) {
            state.lastMaterial = breakData.material();
            state.lastBreakNanos = now;
            return;
        }

        double actualMs = (now - state.lastBreakNanos) / 1_000_000.0D;
        state.lastBreakNanos = now;
        state.lastMaterial = breakData.material();

        // A pause spans a teleport or world change, not a break interval.
        if (actualMs > d("ignore-above-ms", 10_000.0D)) {
            state.streak = 0;
            return;
        }

        double expectedMs = expectedBreakMs(profile, breakData.material());
        if (expectedMs <= 0.0D) {
            return;
        }

        double minimumMs = expectedMs * scaled(profile, "minimum-time-ratio", 0.60D);

        if (actualMs < minimumMs) {
            state.streak++;
        } else {
            state.streak = Math.max(0, state.streak - 1);
        }

        if (state.streak >= i("required-flags", 5)) {
            flag(profile, 1.5D,
                    "broke %s in %.0fms (expected %.0fms, ratio=%.2f) streak=%d",
                    breakData.material(), actualMs, expectedMs,
                    actualMs / expectedMs, state.streak);
            state.streak = 0;
        }
    }

    /**
     * Expected break time in ms, or 0 when the block breaks in a single tick so the
     * caller can skip it.
     */
    private double expectedBreakMs(PlayerProfile profile, String material) {
        double hardness = number(profile, "block-hardness-for-" + material, -1.0D);
        if (hardness < 0.0D) {
            // Fallback to the block below the player, which ProfileListener always feeds.
            hardness = number(profile, "block-below-hardness", 1.0D);
        }
        if (hardness < 0.05D) {
            return 0.0D;
        }

        String tool = (String) profile.attribute("tool-type");
        boolean correctTool = isCorrectToolFor(tool, material);
        double toolSpeed = correctTool ? toolBaseSpeed(tool) : 1.0D;

        int efficiency = (int) number(profile, "tool-efficiency", 0.0D);
        if (efficiency > 0) {
            toolSpeed += (double) (efficiency * efficiency + 1);
        }

        int haste = (int) number(profile, "haste-amplifier", 0.0D);
        if (haste > 0) {
            toolSpeed *= 1.0D + 0.2D * (haste + 1);
        }

        boolean underwater = Boolean.TRUE.equals(profile.attribute("in-liquid"));
        int aquaAffinity = (int) number(profile, "aqua-affinity", 0.0D);
        if (underwater && aquaAffinity == 0) {
            toolSpeed /= 5.0D;
        }

        boolean airborne = !Boolean.TRUE.equals(profile.attribute("server-on-ground"));
        if (airborne) {
            toolSpeed /= 5.0D;
        }

        boolean conduitPower = Boolean.TRUE.equals(profile.attribute("conduit-power"));
        if (underwater && conduitPower) {
            toolSpeed *= 1.5D;
        }

        double damagePerTick = toolSpeed / hardness;
        damagePerTick /= correctTool ? 30.0D : 100.0D;

        if (damagePerTick >= 1.0D) {
            return 0.0D;
        }

        double ticksToBreak = 1.0D / damagePerTick;
        return ticksToBreak * 50.0D;
    }

    private double toolBaseSpeed(String tool) {
        if (tool == null) {
            return 1.0D;
        }
        if (tool.startsWith("WOODEN_")) return 2.0D;
        if (tool.startsWith("STONE_")) return 4.0D;
        if (tool.startsWith("IRON_")) return 6.0D;
        if (tool.startsWith("DIAMOND_")) return 8.0D;
        if (tool.startsWith("NETHERITE_")) return 9.0D;
        if (tool.startsWith("GOLDEN_")) return 12.0D;
        return 1.0D;
    }

    private boolean isCorrectToolFor(String tool, String material) {
        if (tool == null) {
            return false;
        }
        if (material.endsWith("_ORE") || material.contains("STONE") || material.contains("DEEPSLATE")
                || material.equals("OBSIDIAN") || material.equals("CRYING_OBSIDIAN")
                || material.equals("ANCIENT_DEBRIS") || material.contains("_BLOCK")
                && !material.contains("WOOD")) {
            return tool.endsWith("_PICKAXE");
        }
        if (material.endsWith("_LOG") || material.endsWith("_WOOD") || material.endsWith("_PLANKS")
                || material.startsWith("STRIPPED_")) {
            return tool.endsWith("_AXE");
        }
        if (material.equals("DIRT") || material.equals("GRASS_BLOCK")
                || material.equals("SAND") || material.equals("RED_SAND")
                || material.equals("GRAVEL") || material.equals("CLAY")
                || material.contains("SNOW")) {
            return tool.endsWith("_SHOVEL");
        }
        if (material.equals("COBWEB") || material.endsWith("_LEAVES")
                || material.equals("TALL_GRASS") || material.equals("SHORT_GRASS")) {
            return tool.equals("SHEARS");
        }
        return false;
    }

    private double number(PlayerProfile profile, String key, double def) {
        Object value = profile.attribute(key);
        return value instanceof Number n ? n.doubleValue() : def;
    }

    @Override
    public void onQuit(PlayerProfile profile) {
        states.remove(profile.uuid());
    }
}