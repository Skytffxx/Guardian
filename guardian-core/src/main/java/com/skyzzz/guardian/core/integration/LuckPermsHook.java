package com.skyzzz.guardian.core.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Guardian reads permissions through Bukkit's API, which LuckPerms implements, so the
 * only LuckPerms-specific work here is resolving exemption groups.
 */
public final class LuckPermsHook {

    private final boolean available;

    public LuckPermsHook() {
        available = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
    }

    public boolean available() {
        return available;
    }

    /** Primary group, or "default" when LuckPerms is absent. */
    public String primaryGroup(Player player) {
        if (!available) {
            return "default";
        }
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            return user == null ? "default" : user.getPrimaryGroup();
        } catch (Throwable ignored) {
            return "default";
        }
    }
}