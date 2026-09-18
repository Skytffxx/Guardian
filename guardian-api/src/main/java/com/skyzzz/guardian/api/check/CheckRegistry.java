package com.skyzzz.guardian.api.check;

import com.skyzzz.guardian.api.data.AttackData;
import com.skyzzz.guardian.api.data.BlockBreakData;
import com.skyzzz.guardian.api.data.BlockPlaceData;
import com.skyzzz.guardian.api.data.MoveData;
import com.skyzzz.guardian.api.data.PacketData;
import com.skyzzz.guardian.api.player.PlayerProfile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CheckRegistry {

    void register(Check check);

    void unregister(String name);

    Optional<Check> get(String name);

    Collection<Check> all();

    List<Check> byCategory(CheckCategory category);

    boolean setEnabled(String name, boolean enabled);

    /** Re-reads config and re-binds every registered check. */
    void reloadAll();

    // ---- dispatch ---------------------------------------------------------

    void dispatchPacketReceive(PlayerProfile profile, PacketData data);

    void dispatchPacketSend(PlayerProfile profile, PacketData data);

    void dispatchMove(PlayerProfile profile, MoveData data);

    void dispatchAttack(PlayerProfile profile, AttackData data);

    void dispatchBlockPlace(PlayerProfile profile, BlockPlaceData data);

    void dispatchBlockBreak(PlayerProfile profile, BlockBreakData data);

    void dispatchTick(PlayerProfile profile);

    void dispatchJoin(PlayerProfile profile);

    void dispatchQuit(PlayerProfile profile);
}