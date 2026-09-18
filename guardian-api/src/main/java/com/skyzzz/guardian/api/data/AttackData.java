package com.skyzzz.guardian.api.data;

/**
 * One attack/interact-entity packet, enriched by core with the server-side truth
 * (real hitbox distance, line of sight) so combat checks are pure math.
 */
public record AttackData(
        int targetEntityId,
        String targetType,
        double eyeX, double eyeY, double eyeZ,
        float yaw, float pitch,
        /** Distance from the attacker's eye to the closest point of the target hitbox. */
        double hitboxDistance,
        /** True if a server-side raycast from the eye reaches the target. */
        boolean lineOfSight,
        /** False when the entity id could not be resolved (despawned / fake). */
        boolean targetResolved,
        /** True when the attack packet also carried a swing animation in the same tick. */
        boolean swung,
        long timestampNanos
) {
}