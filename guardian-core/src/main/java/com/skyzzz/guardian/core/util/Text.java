package com.skyzzz.guardian.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.ChatColor;

public final class Text {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component miniMessage(String input) {
        if (input == null) {
            return Component.empty();
        }
        return MINI.deserialize(input);
    }

    /** Legacy colour codes for configs written before MiniMessage support. */
    public static String legacy(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }
}