package me.raikou.duels.listener;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

import java.util.List;

public class MotdListener implements Listener {

    private final DuelsPlugin plugin;

    public MotdListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        if (!plugin.getConfig().getBoolean("motd.enabled", false)) {
            return;
        }

        List<String> lines = plugin.getConfig().getStringList("motd.lines");
        if (lines.isEmpty())
            return;

        // Join lines with newline
        String rawMotd = String.join("<newline>", lines);

        // Placeholders
        rawMotd = rawMotd.replace("%online%", String.valueOf(event.getNumPlayers()))
                .replace("%max%", String.valueOf(event.getMaxPlayers()))
                .replace("%version%", plugin.getDescription().getVersion())
                .replace("%date%", java.time.LocalDate.now().toString());

        // Parse MiniMessage
        Component motd = MessageUtil.parse(rawMotd);

        event.motd(motd);
    }
}
