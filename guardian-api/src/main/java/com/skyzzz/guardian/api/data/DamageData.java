package com.skyzzz.guardian.api.data;

/**
 * One hit that landed damage, with the server-side state at impact. The damage event
 * fires after vanilla has already decided whether the hit was a crit, so this is the
 * authoritative record for crit validation.
 */
public record DamageData(
        String victimType,
        double finalDamage,
        boolean attackerAirborne,
        double attackerFallDistance,
        boolean attackerOnClimbable,
        boolean attackerInWater,
        boolean attackerHasBlindness,
        boolean attackerPassenger,
        boolean attackerSprinting,
        boolean critical,    // vanilla applied the 1.5x crit multiplier
        long timestampNanos
) {
}