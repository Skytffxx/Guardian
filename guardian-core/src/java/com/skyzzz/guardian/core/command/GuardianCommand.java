package com.skyzzz.guardian.core.command;

import com.skyzzz.guardian.api.check.Check;
import com.skyzzz.guardian.api.check.CheckCategory;
import com.skyzzz.guardian.api.player.PlayerProfile;
import com.skyzzz.guardian.api.violation.ViolationRecord;
import com.skyzzz.guardian.core.GuardianPlugin;
import com.skyzzz.guardian.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class GuardianCommand implements CommandExecutor, TabCompleter {

    private final GuardianPlugin plugin;

    public GuardianCommand(GuardianPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "profile" -> profile(sender, args);
            case "check" -> check(sender, args);
            case "alerts" -> alerts(sender);
            case "debug" -> debug(sender, args);
            case "history" -> history(sender, args);
            case "reload" -> reload(sender);
            case "help" -> help(sender, label);
            default -> help(sender, label);
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(Text.miniMessage("<gradient:#ffcc00:#ff6600><bold>Guardian</bold></gradient> "
                + "<gray>by skyzzz"));
        sender.sendMessage(Text.miniMessage("<yellow>/" + label + " profile <player> <gray>— live check status + VL breakdown"));
        sender.sendMessage(Text.miniMessage("<yellow>/" + label + " check <name> <on|off> <gray>— toggle a check"));
        sender.sendMessage(Text.miniMessage("<yellow>/" + label + " alerts <gray>— toggle live flag notifications"));
        sender.sendMessage(Text.miniMessage("<yellow>/" + label + " debug <check|all> <gray>— verbose per-check values"));
        sender.sendMessage(Text.miniMessage("<yellow>/" + label + " history <player> [limit] <gray>— violation log"));
        sender.sendMessage(Text.miniMessage("<yellow>/" + label + " reload <gray>— reload config + messages"));
    }

    private void profile(CommandSender sender, String[] args) {
        if (!sender.hasPermission("guardian.command.profile")) {
            noPermission(sender);
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.miniMessage("<red>Usage: /guardian profile <player>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Text.miniMessage("<red>Player not online: " + args[1]));
            return;
        }
        PlayerProfile profile = plugin.profileManager().get(target.getUniqueId());
        if (profile == null) {
            sender.sendMessage(Text.miniMessage("<red>No profile for " + args[1]));
            return;
        }

        sender.sendMessage(Text.miniMessage("<gold>Guardian profile: <white>" + profile.name()));
        sender.sendMessage(Text.miniMessage("<gray>Platform: <white>" + profile.platform()
                + " <gray>| Ping: <white>" + profile.ping() + "ms"
                + " <gray>| Threshold scale: <white>"
                + String.format("%.2f", profile.thresholdScale())
                + " <gray>| Tick: <white>" + profile.tick()));

        Map<String, Double> snapshot = profile.violationSnapshot();
        if (snapshot.isEmpty()) {
            sender.sendMessage(Text.miniMessage("<gray>No violations recorded this session."));
            return;
        }
        snapshot.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(entry -> sender.sendMessage(Text.miniMessage(
                        "  <gray>- <white>" + entry.getKey()
                                + " <dark_gray>| <yellow>"
                                + String.format("%.2f", entry.getValue()))));
    }

    private void check(CommandSender sender, String[] args) {
        if (!sender.hasPermission("guardian.command.check")) {
            noPermission(sender);
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.miniMessage("<red>Usage: /guardian check <name> <on|off>"));
            return;
        }
        boolean enable = args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("true");
        if (plugin.checkRegistry().setEnabled(args[1], enable)) {
            sender.sendMessage(Text.miniMessage("<green>" + args[1] + " -> " + (enable ? "enabled" : "disabled")));
        } else {
            sender.sendMessage(Text.miniMessage("<red>Unknown check: " + args[1]));
        }
    }

    private void alerts(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.miniMessage("<red>Console always receives alerts."));
            return;
        }
        if (!sender.hasPermission("guardian.alerts")) {
            noPermission(sender);
            return;
        }
        boolean enabled = plugin.alertManager().toggle(player.getUniqueId());
        sender.sendMessage(Text.miniMessage(enabled
                ? "<green>Guardian alerts enabled."
                : "<gray>Guardian alerts disabled."));
    }

    private void debug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("guardian.command.debug")) {
            noPermission(sender);
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.miniMessage("<red>Debug mode requires an in-game sender."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.miniMessage("<red>Usage: /guardian debug <check|all>"));
            return;
        }
        PlayerProfile profile = plugin.profileManager().get(player.getUniqueId());
        if (profile == null) {
            return;
        }
        String checkName = args[1].toLowerCase(Locale.ROOT);
        boolean nowEnabled = !profile.debugEnabled(checkName);
        profile.setDebug(checkName, nowEnabled);
        player.sendMessage(Text.miniMessage("<gray>Debug for <white>" + checkName + " <gray>is now "
                + (nowEnabled ? "<green>ON" : "<red>OFF")));
    }

    private void history(CommandSender sender, String[] args) {
        if (!sender.hasPermission("guardian.command.history")) {
            noPermission(sender);
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.miniMessage("<red>Usage: /guardian history <player> [limit]"));
            return;
        }
        int limit = args.length >= 3 ? parseOrDefault(args[2], 15) : 15;

        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(Text.miniMessage("<red>Unknown player: " + args[1]));
            return;
        }

        List<ViolationRecord> records = plugin.violations().history(target.getUniqueId(), limit);
        if (records.isEmpty()) {
            sender.sendMessage(Text.miniMessage("<gray>No history for " + args[1]));
            return;
        }
        sender.sendMessage(Text.miniMessage("<gold>Guardian history: <white>" + args[1]
                + " <gray>(" + records.size() + " entries)"));
        for (ViolationRecord record : records) {
            sender.sendMessage(Text.miniMessage("<gray>- <white>" + record.checkName()
                    + " <dark_gray>| vl <yellow>" + String.format("%.2f", record.vl())
                    + " <dark_gray>| <gray>" + record.debug()));
        }
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("guardian.command.reload")) {
            noPermission(sender);
            return;
        }
        plugin.reloadEverything();
        sender.sendMessage(Text.miniMessage("<green>Guardian reloaded."));
    }

    private void noPermission(CommandSender sender) {
        sender.sendMessage(Text.miniMessage("<red>You do not have permission for that."));
    }

    private int parseOrDefault(String raw, int def) {
        try {
            return Math.max(1, Math.min(200, Integer.parseInt(raw)));
        } catch (NumberFormatException exception) {
            return def;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(List.of("profile", "check", "alerts", "debug", "history", "reload", "help"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "profile", "history" -> Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName).forEach(completions::add);
                case "check", "debug" -> plugin.checkRegistry().all().stream()
                        .map(Check::name).forEach(completions::add);
                default -> {
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("check")) {
            completions.addAll(List.of("on", "off"));
        }

        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return completions.stream()
                .filter(entry -> entry.toLowerCase(Locale.ROOT).startsWith(prefix))
                .collect(Collectors.toList());
    }

    /** Exposed so /guardian profile can render category groupings later. */
    public List<String> categories() {
        List<String> result = new ArrayList<>();
        for (CheckCategory category : CheckCategory.values()) {
            result.add(category.key());
        }
        return result;
    }
}