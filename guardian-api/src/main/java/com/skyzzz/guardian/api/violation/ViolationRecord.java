package com.skyzzz.guardian.api.violation;

import java.util.UUID;

public record ViolationRecord(
        long id,
        UUID uuid,
        String playerName,
        String checkName,
        String category,
        double vl,
        String debug,
        long timestampEpochMillis
) {
}