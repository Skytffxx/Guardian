package com.skyzzz.guardian.core.alert;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Plain HTTP webhook — no bot framework, no gateway connection.
 * Sends are queued on a single-thread executor so a slow Discord endpoint can never
 * block the server thread.
 */
public final class DiscordWebhook {

    private static final int COLOR_FLAG = 0xFFA500;
    private static final int COLOR_PUNISH = 0xFF0000;

    private final String url;
    private final HttpClient client;
    private final ExecutorService executor;

    public DiscordWebhook(String url) {
        this.url = url;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Guardian-Webhook");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void send(String player, String check, String category, double vl,
                     String debug, String serverName) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Guardian — " + check);
        embed.addProperty("color", "punishment".equals(category) ? COLOR_PUNISH : COLOR_FLAG);

        JsonArray fields = new JsonArray();
        fields.add(field("Player", player, true));
        fields.add(field("Category", category, true));
        fields.add(field("VL", String.format("%.2f", vl), true));
        if (debug != null && !debug.isBlank()) {
            fields.add(field("Detail", debug, false));
        }
        if (serverName != null && !serverName.isBlank()) {
            fields.add(field("Server", serverName, true));
        }
        embed.add("fields", fields);

        JsonObject payload = new JsonObject();
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);

        executor.execute(() -> post(payload.toString()));
    }

    private JsonObject field(String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value == null || value.isBlank() ? "—" : value);
        field.addProperty("inline", inline);
        return field;
    }

    private void post(String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Guardian-Anticheat")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(8))
                    .build();
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}