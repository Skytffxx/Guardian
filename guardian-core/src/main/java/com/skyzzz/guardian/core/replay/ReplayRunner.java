package com.skyzzz.guardian.core.replay;

import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.player.PlayerProfile;
import com.skyzzz.guardian.core.GuardianPlugin;
import com.skyzzz.guardian.core.check.CheckRegistryImpl;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * False-positive harness.
 *
 * Feeds a recorded packet log of legitimate gameplay straight through the check
 * pipeline, so FP rates get measured before deployment instead of discovered by
 * angry players after it.
 *
 * Log format — one record per line, comma-separated:
 *   MOVE,<x>,<y>,<z>,<lastX>,<lastY>,<lastZ>,<yaw>,<pitch>,<lastYaw>,<lastPitch>,<onGround>,<lastOnGround>,<deltaNanos>
 *   TICK,<deltaNanos>
 *
 * Usage: /guardian replay <file> (staff-only, on a test server).
 */
public final class ReplayRunner {

    private final GuardianPlugin plugin;

    public ReplayRunner(GuardianPlugin plugin) {
        this.plugin = plugin;
    }

    public Result run(Path file, PlayerProfile profile, CheckRegistryImpl registry) throws IOException {
        long start = System.nanoTime();
        long lines = 0L;
        long flagsBefore = totalFlags(profile);

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                lines++;
                dispatch(profile, registry, line);
                profile.advanceTick();
            }
        }

        long elapsed = System.nanoTime() - start;
        long flagsAfter = totalFlags(profile);
        return new Result(lines, flagsAfter - flagsBefore, elapsed);
    }

    private void dispatch(PlayerProfile profile, CheckRegistryImpl registry, String line) {
        String[] parts = line.split(",");
        switch (parts[0]) {
            case "MOVE" -> {
                if (parts.length < 15) {
                    return;
                }
                MoveData move = new MoveData(
                        d(parts[1]), d(parts[2]), d(parts[3]),
                        d(parts[4]), d(parts[5]), d(parts[6]),
                        (float) d(parts[7]), (float) d(parts[8]),
                        (float) d(parts[9]), (float) d(parts[10]),
                        Boolean.parseBoolean(parts[11]), Boolean.parseBoolean(parts[12]),
                        true, true, 1.62D,
                        profile.tick() * 50_000_000L);
                registry.dispatchMove(profile, move);
            }
            case "TICK" -> registry.dispatchTick(profile);
            default -> {
                // Unknown record type — ignore rather than abort the whole replay.
            }
        }
    }

    private double d(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return 0.0D;
        }
    }

    private long totalFlags(PlayerProfile profile) {
        return profile.violationSnapshot().values().stream()
                .mapToLong(value -> value > 0.0D ? 1L : 0L)
                .sum();
    }

    public record Result(long recordsProcessed, long violationsProduced, long elapsedNanos) {

        public double averageMicrosPerRecord() {
            return recordsProcessed == 0 ? 0.0D
                    : (elapsedNanos / 1000.0D) / recordsProcessed;
        }

        /** Fraction of processed records that produced a violation. */
        public double falsePositiveRate() {
            return recordsProcessed == 0 ? 0.0D
                    : (double) violationsProduced / recordsProcessed;
        }

        public List<String> summary() {
            return List.of(
                    "records=" + recordsProcessed,
                    "violations=" + violationsProduced,
                    String.format("fp-rate=%.4f%%", falsePositiveRate() * 100.0D),
                    String.format("avg=%.3f µs/record", averageMicrosPerRecord()));
        }
    }
}