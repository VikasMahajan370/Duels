package me.raikou.duels.anticheat;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.anticheat.checks.FlightCheck;
import me.raikou.duels.anticheat.checks.KillAuraCheck;
import me.raikou.duels.anticheat.checks.ReachCheck;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main anti-cheat manager that coordinates all checks.
 * Strict but fair detection with immediate action for flight hacks.
 */
public class AntiCheatManager implements Listener {

    private final DuelsPlugin plugin;
    private final boolean enabled;
    private final List<Check> checks = new ArrayList<>();
    private final Map<UUID, Map<String, Integer>> violations = new ConcurrentHashMap<>();
    private final Map<UUID, me.raikou.duels.anticheat.data.PlayerData> playerDataMap = new ConcurrentHashMap<>();
    private final String alertPermission = "duels.anticheat.alerts";

    public AntiCheatManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("anticheat.enabled", true);

        if (enabled) {
            registerChecks();
            Bukkit.getPluginManager().registerEvents(this, plugin);
            // We removed the old scheduler in favor of event-based + tick-based approach
            // But we keep a light tick task for things that must run per-tick
            startTickTask();
            plugin.getLogger().info("Anti-Cheat system enabled with " + checks.size() + " checks!");
        }
    }

    private void registerChecks() {
        checks.add(new FlightCheck(plugin));
        checks.add(new KillAuraCheck(plugin));
        checks.add(new ReachCheck(plugin));
        // Add new checks here when created
    }

    public me.raikou.duels.anticheat.data.PlayerData getPlayerData(Player player) {
        return playerDataMap.computeIfAbsent(player.getUniqueId(),
                k -> new me.raikou.duels.anticheat.data.PlayerData(player));
    }

    private void startTickTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (plugin.getDuelManager().getDuel(player) == null)
                        continue;

                    me.raikou.duels.anticheat.data.PlayerData data = getPlayerData(player);
                    data.update(player); // Update ping etc.
                }
            }
        }.runTaskTimer(plugin, 1L, 20L); // Update slow data every second
    }

    @EventHandler
    public void onMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (!enabled)
            return;
        Player player = event.getPlayer();

        // Optim: Only check if moving specific distance or valid duel
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        if (plugin.getDuelManager().getDuel(player) == null)
            return;

        me.raikou.duels.anticheat.data.PlayerData data = getPlayerData(player);

        for (Check check : checks) {
            if (check.isEnabled() && check.onMove(player, event.getTo(), event.getFrom(), data)) {
                handleViolation(player, check, "Movement Violation");
                // For movement updates, we might want to cancel or set back
                // But let the specific check handle returning 'true' to signal violation
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled)
            return;
        if (!(event.getDamager() instanceof Player attacker))
            return;

        if (plugin.getDuelManager().getDuel(attacker) == null)
            return;

        me.raikou.duels.anticheat.data.PlayerData data = getPlayerData(attacker);
        data.setLastAttackTime(System.currentTimeMillis());

        for (Check check : checks) {
            if (check.isEnabled() && check.onAttack(attacker, event.getEntity(), data)) {
                event.setCancelled(true);
                handleViolation(attacker, check, "Combat Violation");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        violations.remove(uuid);
        playerDataMap.remove(uuid);
    }

    // Cleanup on join to be safe
    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        violations.remove(event.getPlayer().getUniqueId());
        playerDataMap.remove(event.getPlayer().getUniqueId());
    }

    private void handleViolation(Player player, Check check, String details) {
        flagPlayer(player, check, details);
    }

    /**
     * Flag a player for a violation
     */
    public void flagPlayer(Player player, Check check, String details) {
        UUID uuid = player.getUniqueId();

        violations.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        Map<String, Integer> playerViolations = violations.get(uuid);
        int vl = playerViolations.getOrDefault(check.getName(), 0) + 1;
        playerViolations.put(check.getName(), vl);

        alertStaff(player, check, vl, details);
        logToDiscord(player, check, vl, details);

        if (vl >= check.getMaxViolations()) {
            executeAction(player, check, vl);
        }
    }

    /**
     * Alert online staff about violation
     */
    private void alertStaff(Player player, Check check, int vl, String details) {
        Component alert = Component.text()
                .append(Component.text("[AC] ", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" failed ", NamedTextColor.GRAY))
                .append(Component.text(check.getName(), NamedTextColor.GOLD))
                .append(Component.text(" [VL:", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(vl), getViolationColor(vl, check.getMaxViolations())))
                .append(Component.text("/", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(check.getMaxViolations()), NamedTextColor.RED))
                .append(Component.text("] ", NamedTextColor.GRAY))
                .append(Component.text(details, NamedTextColor.DARK_GRAY))
                .build();

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(alertPermission)) {
                staff.sendMessage(alert);
            }
        }

        plugin.getLogger().warning("[AC] " + player.getName() + " failed " + check.getName() +
                " [VL:" + vl + "/" + check.getMaxViolations() + "] " + details);
    }

    /**
     * Log violation to Discord
     */
    private void logToDiscord(Player player, Check check, int vl, String details) {
        if (plugin.getDiscordManager() == null)
            return;

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        String title = "⚠️ Anti-Cheat Alert";
        String description = String.format(
                "**Player:** %s\n**Check:** %s\n**Violations:** %d/%d\n**Details:** %s\n**Time:** %s",
                player.getName(),
                check.getName(),
                vl,
                check.getMaxViolations(),
                details,
                timestamp);

        plugin.getDiscordManager().sendEmbed(title, description,
                vl >= check.getMaxViolations() ? 0xFF0000 : 0xFFAA00);
    }

    /**
     * Execute action when max violations reached
     */
    private void executeAction(Player player, Check check, int vl) {
        String action = plugin.getConfig().getString("anticheat.action", "kick");

        switch (action.toLowerCase()) {
            case "kick":
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.kick(Component.text()
                            .append(Component.text("Duels Anti-Cheat\n\n", NamedTextColor.RED, TextDecoration.BOLD))
                            .append(Component.text("You have been kicked for suspicious activity.\n",
                                    NamedTextColor.WHITE))
                            .append(Component.text("Detection: " + check.getName() + "\n", NamedTextColor.GRAY))
                            .append(Component.text("Violations: " + vl, NamedTextColor.DARK_GRAY))
                            .build());
                });
                break;
            case "ban":
                break; // Implement ban if needed
        }

        // Reset violations after action
        violations.get(player.getUniqueId()).put(check.getName(), 0);
    }

    private NamedTextColor getViolationColor(int vl, int max) {
        double ratio = (double) vl / max;
        if (ratio >= 0.8)
            return NamedTextColor.RED;
        if (ratio >= 0.5)
            return NamedTextColor.GOLD;
        if (ratio >= 0.3)
            return NamedTextColor.YELLOW;
        return NamedTextColor.GREEN;
    }

    public int getViolations(Player player, String checkName) {
        Map<String, Integer> playerVl = violations.get(player.getUniqueId());
        if (playerVl == null)
            return 0;
        return playerVl.getOrDefault(checkName, 0);
    }

    public boolean isEnabled() {
        return enabled;
    }
}
