package me.raikou.duels.discord;

import me.raikou.duels.DuelsPlugin;
import org.bukkit.Bukkit;

import java.awt.Color;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class DiscordLogger {

    private final DuelsPlugin plugin;
    private final HttpClient client;

    public DiscordLogger(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void log(String title, String description, Color color, Map<String, String> fields) {
        if (!plugin.getConfig().getBoolean("discord.enabled", false))
            return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String mode = plugin.getConfig().getString("discord.mode", "WEBHOOK").toUpperCase();
                String payload = buildJsonPayload(title, description, color, fields);

                if ("WEBHOOK".equals(mode)) {
                    String url = plugin.getConfig().getString("discord.webhook-url");
                    if (url == null || url.isEmpty() || url.contains("YOUR_WEBHOOK_URL"))
                        return;
                    sendRequest(url, payload, null);
                } else if ("BOT".equals(mode)) {
                    String token = plugin.getConfig().getString("discord.bot-token");
                    String channelId = plugin.getConfig().getString("discord.channel-id");
                    if (token == null || channelId == null || token.contains("YOUR_BOT_TOKEN"))
                        return;

                    String url = "https://discord.com/api/v10/channels/" + channelId + "/messages";
                    sendRequest(url, payload, token);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send Discord log: " + e.getMessage());
            }
        });
    }

    private void sendRequest(String url, String jsonPayload, String botToken) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload));

            if (botToken != null) {
                builder.header("Authorization", "Bot " + botToken);
            }

            client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String buildJsonPayload(String title, String description, Color color, Map<String, String> fields) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"embeds\": [{");
        json.append("\"title\": \"").append(escape(title)).append("\",");
        json.append("\"description\": \"").append(escape(description)).append("\",");

        int colorInt = (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
        json.append("\"color\": ").append(colorInt);

        if (fields != null && !fields.isEmpty()) {
            json.append(",\"fields\": [");
            boolean first = true;
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                if (!first)
                    json.append(",");
                json.append("{");
                json.append("\"name\": \"").append(escape(entry.getKey())).append("\",");
                json.append("\"value\": \"").append(escape(entry.getValue())).append("\",");
                json.append("\"inline\": true");
                json.append("}");
                first = false;
            }
            json.append("]");
        }

        // Footer timestamp
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                .ofPattern("dd/MM/yyyy HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault());
        String date = formatter.format(java.time.Instant.now());

        json.append(",\"footer\": {\"text\": \"by devRaikou - ").append(date)
                .append("\"}");

        json.append("}]");
        json.append("}");
        return json.toString();
    }

    private String escape(String text) {
        if (text == null)
            return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
