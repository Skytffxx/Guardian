package com.skyzzz.guardian.api.check;

import com.skyzzz.guardian.api.data.AttackData;
import com.skyzzz.guardian.api.data.BlockBreakData;
import com.skyzzz.guardian.api.data.BlockPlaceData;
import com.skyzzz.guardian.api.data.DamageData;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.data.PacketData;
import com.skyzzz.guardian.api.player.PlayerProfile;

/**
 * One detection. Every hook is defaulted, so a check overrides only what it needs.
 * Move/attack hooks run on the main thread; packet hooks may run async, so check
 * state must be thread-safe.
 */
public interface Check {

    /** Config, command and log identifier; lowercase, no spaces. */
    String name();

    CheckCategory category();

    CheckSettings settings();

    boolean isEnabled();

    void setEnabled(boolean enabled);

    void bind(CheckSettings settings);

    default void onPacketReceive(PlayerProfile profile, PacketData packet) {
    }

    default void onPacketSend(PlayerProfile profile, PacketData packet) {
    }

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

    default void onDamage(PlayerProfile profile, DamageData damage) {
    }

    default void onJoin(PlayerProfile profile) {
    }

    default void onQuit(PlayerProfile profile) {
    }

    default void onReload() {
    }
}