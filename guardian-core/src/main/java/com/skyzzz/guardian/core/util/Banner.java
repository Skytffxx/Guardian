package com.skyzzz.guardian.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;

public final class Banner {

    private static final TextColor GOLD   = TextColor.fromHexString("#FFD166");
    private static final TextColor AMBER  = TextColor.fromHexString("#FFB84D");
    private static final TextColor ORANGE = TextColor.fromHexString("#FF8C42");
    private static final TextColor EMBER  = TextColor.fromHexString("#E85D04");
    private static final TextColor LIGHT  = TextColor.fromHexString("#F2F2F2");
    private static final TextColor MUTED  = TextColor.fromHexString("#8A8A8A");
    private static final TextColor DARK   = TextColor.fromHexString("#3A3A3A");
    private static final TextColor GREEN  = TextColor.fromHexString("#5BD75B");
    private static final TextColor WARN   = TextColor.fromHexString("#FFB454");
    private static final TextColor ERROR  = TextColor.fromHexString("#FF5555");

    private static final String[] LOGO = {
            " ██████╗ ██╗   ██╗ █████╗ ██████╗ ██████╗ ██╗ █████╗ ███╗   ██╗",
            "██╔════╝ ██║   ██║██╔══██╗██╔══██╗██╔══██╗██║██╔══██╗████╗  ██║",
            "██║  ███╗██║   ██║███████║██████╔╝██║  ██║██║███████║██╔██╗ ██║",
            "██║   ██║██║   ██║██╔══██║██╔══██╗██║  ██║██║██╔══██║██║╚██╗██║",
            "╚██████╔╝╚██████╔╝██║  ██║██║  ██║██████╔╝██║██║  ██║██║ ╚████║",
            " ╚═════╝  ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═╝╚═════╝ ╚═╝╚═╝  ╚═╝╚═╝  ╚═══╝",
    };

    private static final TextColor[] ROW_COLORS = { GREEN, GREEN, GREEN, ERROR, ERROR, ERROR };

    private static final int WIDTH = computeWidth();

    private Banner() {
    }

    public static void print(String version) {
        blank();
        rule();
        blank();

        for (int row = 0; row < LOGO.length; row++) {
            send(Component.text(LOGO[row], ROW_COLORS[row])
                    .decoration(TextDecoration.BOLD, true));
        }

        blank();
        send(Component.text(center("Anticheat  ·  by skyzzz"), GOLD)
                .decoration(TextDecoration.BOLD, false));
        send(Component.text(center("v" + version + "  ·  Crossplay ready"), MUTED));
        blank();
        rule();
        blank();
    }

    public static void step(String message) {
        send(Component.text("  ")
                .append(Component.text("✔  ", GREEN).decoration(TextDecoration.BOLD, true))
                .append(Component.text(message, LIGHT)));
    }

    public static void step(String message, String detail) {
        send(Component.text("  ")
                .append(Component.text("✔  ", GREEN).decoration(TextDecoration.BOLD, true))
                .append(Component.text(message, LIGHT))
                .append(Component.text("  " + detail, MUTED)));
    }

    public static void warn(String message) {
        send(Component.text("  ")
                .append(Component.text("⚠  ", WARN).decoration(TextDecoration.BOLD, true))
                .append(Component.text(message, WARN)));
    }

    public static void fail(String message) {
        send(Component.text("  ")
                .append(Component.text("✘  ", ERROR).decoration(TextDecoration.BOLD, true))
                .append(Component.text(message, ERROR)));
    }

    public static void info(String message) {
        send(Component.text("  ")
                .append(Component.text("·  ", MUTED))
                .append(Component.text(message, LIGHT)));
    }

    public static void ready(String message) {
        send(Component.text("  ")
                .append(Component.text("●  ", GREEN).decoration(TextDecoration.BOLD, true))
                .append(Component.text(message, LIGHT).decoration(TextDecoration.BOLD, true)));
    }

    public static void blank() {
        send(Component.empty());
    }

    public static void rule() {
        send(Component.text("  " + "─".repeat(WIDTH), DARK));
    }

    private static int computeWidth() {
        int max = 0;
        for (String row : LOGO) {
            max = Math.max(max, row.length());
        }
        return max;
    }

    private static String center(String text) {
        if (text.length() >= WIDTH) {
            return text;
        }
        return " ".repeat((WIDTH - text.length()) / 2) + text;
    }

    private static void send(Component component) {
        Bukkit.getConsoleSender().sendMessage(component);
    }
}