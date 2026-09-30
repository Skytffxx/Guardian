package com.skyzzz.guardian.api.data;

/**
 * One attack packet, enriched by core with the server-side truth (real hitbox distance,
 * line of sight) so combat checks stay pure math.
 */
public record AttackData(
        int targetEntityId,
        String targetType,
        double eyeX, double eyeY, double eyeZ,
        float yaw, float pitch,
        double hitboxDistance,   // eye to closest point of the target's hitbox
        boolean lineOfSight,     // server raycast from the eye reaches the target
        boolean targetResolved,  // false when the id is unknown (despawned or fake)
        boolean swung,           // attack carried a swing animation in the same tick
        long timestampNanos
) {
}