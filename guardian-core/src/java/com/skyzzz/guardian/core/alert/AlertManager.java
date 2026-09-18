package com.skyzzz.guardian.core.alert;

import com.skyzzz.guardian.api.check.Check;
import com.skyzzz.guardian.api.player.PlayerProfile;
import com.skyzzz.guardian.core.GuardianPlugin;
import com.skyzzz.guardian.core.config.GuardianConfig;
import com.skyzzz.guardian.core.config.Messages;
import com.skyzzz.guardian.core.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live staff feed. Chat alerts go to permission holders; Discord alerts go through a
 * plain HTTP webhook so no bot framework is required.
 */
public final class AlertManager {

    private static final String PERMISSION = "guardian.alerts";

    private final GuardianPlugin plugin;
    private final GuardianConfig config;
    private final Messages messages;
    private final Set<java.util.UUID> alertToggles = ConcurrentHashMap.newKeySet();

    private DiscordWebhook webhook;

    public AlertManager(GuardianPlugin plugin, GuardianConfig config, Messages messages) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        reload();
    }

    public void reload() {
        if (webhook != null) {
            webhook.shutdown();
            webhook = null;
        }
        if (config.getBoolean("discord.enabled", false)) {
            String url = config.getString("discord.webhook-url", "");
            if (!url.isBlank() && !url.startsWith("https://discord.com/api/webhooks/CHANGE_ME")) {
                this.webhook = new DiscordWebhook(url);
            }
        }
    }

    public void shutdown() {
        if (webhook != null) {
            webhook.shutdown();
        }
    }

    public boolean toggle(java.util.UUID uuid) {
        if (alertToggles.contains(uuid)) {
            alertToggles.remove(uuid);
            return false;
        }
        alertToggles.add(uuid);
        return true;
    }

    public boolean hasAlerts(java.util.UUID uuid) {
        return alertToggles.contains(uuid);
    }

    public void alert(PlayerProfile profile, Check check, double vl, String debug) {
        String rendered = messages.raw("alert-format")
                .replace("{player}", profile.name())
                .replace("{check}", check.name())
                .replace("{category}", check.category().key())
                .replace("{vl}", String.format("%.2f", vl))
                .replace("{debug}", debug)
                .replace("{ping}", String.valueOf(profile.ping()))
                .replace("{platform}", profile.platform().name());

        Component component = Text.miniMessage(rendered);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(PERMISSION) && alertToggles.contains(online.getUniqueId())) {
                online.sendMessage(component);
            }
        }
        plugin.getLogger().info("[ALERT] " + rendered);

        if (webhook != null && vl >= config.getDouble("discord.minimum-vl", 3.0D)) {
            webhook.send(profile.name(), check.name(), check.category().key(), vl, debug,
                    config.getBoolean("discord.include-server-name", true)
                            ? Bukkit.getServer().getName() : null);
        }
    }

    public void punishment(PlayerProfile profile, String source, double vl, double threshold) {
        String rendered = messages.raw("punishment-format")
                .replace("{player}", profile.name())
                .replace("{source}", source)
                .replace("{vl}", String.format("%.2f", vl))
                .replace("{threshold}", String.format("%.2f", threshold));

        Component component = Text.miniMessage(rendered);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(PERMISSION)) {
                online.sendMessage(component);
            }
        }
        if (webhook != null) {
            webhook.send(profile.name(), source, "punishment", vl,
                    "threshold=" + threshold, null);
        }
    }
}