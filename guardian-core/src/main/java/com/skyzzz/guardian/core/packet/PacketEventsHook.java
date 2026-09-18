package com.skyzzz.guardian.core.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.skyzzz.guardian.core.GuardianPlugin;

public final class PacketEventsHook {

    private final GuardianPlugin plugin;
    private PacketListenerCommon registration;

    public PacketEventsHook(GuardianPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean start(GuardianPacketListener listener) {
        if (plugin.getServer().getPluginManager().getPlugin("packetevents") == null) {
            return false;
        }
        try {
            registration = PacketEvents.getAPI().getEventManager()
                    .registerListener(listener, PacketListenerPriority.NORMAL);
            return true;
        } catch (Throwable throwable) {
            plugin.getLogger().severe("PacketEvents registration failed: " + throwable.getMessage());
            return false;
        }
    }

    public void stop() {
        if (registration != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(registration);
            registration = null;
        }
    }
}