package com.skyzzz.guardian.api.data;

/**
 * One attack that landed damage, enriched by core with the server-side state at
 * the moment of impact. This is the authoritative record for crit validation,
 * because the damage event fires after the server has already decided whether the
 * hit was a crit and how much damage was dealt.
 *
 * The {@code critical} field is true when the vanilla damage formula detected crit
 * conditions server-side. Fake-crit detection compares that against whether the
 * client's trajectory could legitimately satisfy those conditions.
 */
public record DamageData(
        String victimType,
        double finalDamage,
        /** Server's view of the attacker at impact time. */
        boolean attackerAirborne,
        double attackerFallDistance,
        boolean attackerOnClimbable,
        boolean attackerInWater,
        boolean attackerHasBlindness,
        boolean attackerPassenger,
        boolean attackerSprinting,
        /** True when vanilla applied the 1.5x crit multiplier to this hit. */
        boolean critical,
        long timestampNanos
) {
}