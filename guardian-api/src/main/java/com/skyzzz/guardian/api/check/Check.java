package com.skyzzz.guardian.api.check;

import com.skyzzz.guardian.api.data.AttackData;
import com.skyzzz.guardian.api.data.BlockBreakData;
import com.skyzzz.guardian.api.data.BlockPlaceData;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.data.PacketData;
import com.skyzzz.guardian.api.player.PlayerProfile;

/**
 * One detection. Every hook is defaulted so a check only overrides what it needs.
 * Implementations must be thread-safe with respect to their own state: move/attack
 * hooks run on the main thread, packet hooks may run async.
 */
public interface Check {

    /** Stable identifier used in config, commands and logs. Lowercase, no spaces. */
    String name();

    CheckCategory category();

    CheckSettings settings();

    boolean isEnabled();

    void setEnabled(boolean enabled);

    /** Called on startup and on every reload. */
    void bind(CheckSettings settings);

    // ---- packet level -------------------------------------------------
    default void onPacketReceive(PlayerProfile profile, PacketData packet) {
    }

    default void onPacketSend(PlayerProfile profile, PacketData packet) {
    }

    // ---- gameplay level -----------------------------------------------
    default void onMove(PlayerProfile profile, MoveData move) {
    }

    default void onAttack(PlayerProfile profile, AttackData attack) {
    }

    default void onBlockPlace(PlayerProfile profile, BlockPlaceData place) {
    }

    default void onBlockBreak(PlayerProfile profile, BlockBreakData breakData) {
    }

    default void onTick(PlayerProfile profile) {
    }
    
    default void onDamage(PlayerProfile profile, com.skyzzz.guardian.api.data.DamageData damage) {
    }
    // ---- lifecycle -----------------------------------------------------
    default void onJoin(PlayerProfile profile) {
    }

    default void onQuit(PlayerProfile profile) {
    }

    default void onReload() {
    }
}