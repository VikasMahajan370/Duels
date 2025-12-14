package me.raikou.duels.command;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.stats.PlayerStats;
import me.raikou.duels.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;

public class StatsCommand implements CommandExecutor {

    private final DuelsPlugin plugin;
    private final DecimalFormat df = new DecimalFormat("0.00");

    public StatsCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {

        Player target;

        if (args.length > 0) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                MessageUtil.sendError(sender, "Player not found or offline.");
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Console must specify a player.");
                return true;
            }
            target = (Player) sender;
        }

        PlayerStats stats = plugin.getStatsManager().getStats(target);

        // Calculate K/D
        double kdRatio = 0.0;
        if (stats.getDeaths() > 0) {
            kdRatio = (double) stats.getKills() / stats.getDeaths();
        } else {
            kdRatio = stats.getKills();
        }

        sender.sendMessage(MessageUtil.parse(
                "<newline><gradient:#FFD700:#FFA500><bold>STATISTICS</bold></gradient> <dark_gray>▪</dark_gray> <yellow>"
                        + target.getName() + "</yellow>"));
        sender.sendMessage(MessageUtil.parse("<gray>⚔ Wins:</gray> <green>" + stats.getWins() + "</green>"));
        sender.sendMessage(MessageUtil.parse("<gray>☠ Losses:</gray> <red>" + stats.getLosses() + "</red>"));
        sender.sendMessage(MessageUtil.parse("<gray>🗡 Kills:</gray> <green>" + stats.getKills() + "</green>"));
        sender.sendMessage(MessageUtil.parse("<gray>💀 Deaths:</gray> <red>" + stats.getDeaths() + "</red>"));
        sender.sendMessage(MessageUtil.parse("<gray>📊 K/D Ratio:</gray> <gold>" + df.format(kdRatio) + "</gold>"));
        sender.sendMessage(MessageUtil.parse(" ")); // Empty line footer

        return true;
    }
}
