package me.raikou.duels.util;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.duel.Duel;
import me.raikou.duels.stats.PlayerStats;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * Manages TAB header and footer for all players.
 */
public class TabManager {

    private final DuelsPlugin plugin;

    public TabManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        startUpdater();
    }

    private void startUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getConfig().getBoolean("tab.enabled", true)) {
                    return;
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateTab(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 40L); // Update every 2 seconds
    }

    public void updateTab(Player player) {
        if (!plugin.getConfig().getBoolean("tab.enabled", true)) {
            return;
        }

        List<String> headerLines = plugin.getConfig().getStringList("tab.header");
        List<String> footerLines = plugin.getConfig().getStringList("tab.footer");

        // Build header
        StringBuilder headerBuilder = new StringBuilder();
        for (int i = 0; i < headerLines.size(); i++) {
            String line = applyPlaceholders(headerLines.get(i), player);
            headerBuilder.append(line);
            if (i < headerLines.size() - 1) {
                headerBuilder.append("\n");
            }
        }

        // Build footer
        StringBuilder footerBuilder = new StringBuilder();
        for (int i = 0; i < footerLines.size(); i++) {
            String line = applyPlaceholders(footerLines.get(i), player);
            footerBuilder.append(line);
            if (i < footerLines.size() - 1) {
                footerBuilder.append("\n");
            }
        }

        Component header = MessageUtil.parse(headerBuilder.toString());
        Component footer = MessageUtil.parse(footerBuilder.toString());

        player.sendPlayerListHeaderAndFooter(header, footer);
    }

    private String applyPlaceholders(String text, Player player) {
        // Player info
        text = text.replace("%player%", player.getName());
        text = text.replace("%ping%", String.valueOf(player.getPing()));
        text = text.replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        text = text.replace("%max%", String.valueOf(Bukkit.getMaxPlayers()));
        text = text.replace("%date%", java.time.LocalDate.now().toString());
        text = text.replace("%time%", java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm")));

        // Stats
        PlayerStats stats = plugin.getStatsManager().getStats(player);
        text = text.replace("%wins%", String.valueOf(stats.getWins()));
        text = text.replace("%losses%", String.valueOf(stats.getLosses()));
        text = text.replace("%kills%", String.valueOf(stats.getKills()));
        text = text.replace("%deaths%", String.valueOf(stats.getDeaths()));

        // Duel info
        Duel duel = plugin.getDuelManager().getDuel(player);
        if (duel != null) {
            text = text.replace("%kit%", duel.getKitName() != null ? duel.getKitName() : "None");
            text = text.replace("%status%", "In Duel");

            // Find opponent
            for (java.util.UUID uuid : duel.getPlayers()) {
                if (!uuid.equals(player.getUniqueId())) {
                    Player opponent = Bukkit.getPlayer(uuid);
                    text = text.replace("%opponent%", opponent != null ? opponent.getName() : "Unknown");
                    break;
                }
            }
        } else {
            text = text.replace("%kit%", "None");
            text = text.replace("%status%", "In Lobby");
            text = text.replace("%opponent%", "None");
        }

        // Queue info
        if (plugin.getQueueManager().isInQueue(player)) {
            text = text.replace("%queue%", "Searching...");
        } else {
            text = text.replace("%queue%", "Not in queue");
        }

        return text;
    }
}
