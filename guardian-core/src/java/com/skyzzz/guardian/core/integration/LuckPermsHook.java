package com.skyzzz.guardian.core.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * LuckPerms is used for permission-gated staff commands and exemption ranks.
 * Guardian reads permissions through Bukkit's API, which LuckPerms implements, so the
 * only LuckPerms-specific work is registering exemption groups at startup.
 *
 * Bukkit permissions remain the fallback when LuckPerms is absent.
 */
public final class LuckPermsHook {

    private final boolean available;

    public LuckPermsHook() {
        this.available = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
    }

    public boolean available() {
        return available;
    }

    /** Returns the player's primary group, or "default" when LuckPerms is absent. */
    public String primaryGroup(Player player) {
        if (!available) {
            return "default";
        }
        try {
            net.luckperms.api.LuckPerms luckPerms = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = luckPerms.getUserManager()
                    .getUser(player.getUniqueId());
            return user == null ? "default" : user.getPrimaryGroup();
        } catch (Throwable throwable) {
            return "default";
        }
    }
}