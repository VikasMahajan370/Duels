package me.raikou.duels.anticheat;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.anticheat.checks.FlightCheck;
import me.raikou.duels.anticheat.checks.KillAuraCheck;
import me.raikou.duels.anticheat.checks.ReachCheck;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
    private final Map<UUID, Location> lastSafeLocation = new ConcurrentHashMap<>();
    private final String alertPermission = "duels.anticheat.alerts";

    public AntiCheatManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("anticheat.enabled", true);

        if (enabled) {
            registerChecks();
            Bukkit.getPluginManager().registerEvents(this, plugin);
            startFlightChecker();
            startSafeLocationTracker();
            plugin.getLogger().info("Anti-Cheat system enabled with " + checks.size() + " checks!");
        }
    }

    private void registerChecks() {
        checks.add(new FlightCheck(plugin));
        checks.add(new KillAuraCheck(plugin));
        checks.add(new ReachCheck(plugin));
    }

    /**
     * Run flight check frequently for strict detection
     */
    private void startFlightChecker() {
        Check flightCheck = getCheck("Flight");
        if (flightCheck == null || !flightCheck.isEnabled())
            return;

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // Only check players in duels
                    if (plugin.getDuelManager().getDuel(player) == null)
                        continue;

                    if (flightCheck.check(player)) {
                        handleFlightViolation(player, flightCheck);
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 5L); // Every 0.25 seconds for strict checking
    }

    /**
     * Track last safe location for teleport-back on violation
     */
    private void startSafeLocationTracker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.isOnGround() && !player.isFlying()) {
                        lastSafeLocation.put(player.getUniqueId(), player.getLocation().clone());
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 10L);
    }

    /**
     * Handle flight violation with immediate action
     */
    private void handleFlightViolation(Player player, Check check) {
        UUID uuid = player.getUniqueId();

        // Increment violations
        violations.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        Map<String, Integer> playerViolations = violations.get(uuid);
        int vl = playerViolations.getOrDefault(check.getName(), 0) + 1;
        playerViolations.put(check.getName(), vl);

        // Alert staff
        alertStaff(player, check, vl, "Flight/Hover detected");

        // Immediate action: Teleport to last safe location
        Location safeLoc = lastSafeLocation.get(uuid);
        if (safeLoc != null && vl >= 2) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.teleport(safeLoc);
                player.setVelocity(player.getVelocity().setY(-0.5)); // Force down
            });
        }

        // Log to Discord
        logToDiscord(player, check, vl, "Airborne without permission");

        // Kick after max violations
        if (vl >= check.getMaxViolations()) {
            executeAction(player, check, vl);
        }
    }

    /**
     * Check combat-related hacks on damage
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled)
            return;

        if (!(event.getDamager() instanceof Player attacker))
            return;
        if (!(event.getEntity() instanceof Player victim))
            return;

        // Only check in duels
        if (plugin.getDuelManager().getDuel(attacker) == null)
            return;

        // Run combat checks
        Check killAura = getCheck("KillAura");
        Check reach = getCheck("Reach");

        if (killAura != null && killAura.isEnabled() && killAura.check(attacker, victim)) {
            flagPlayer(attacker, killAura, "Attack angle: " +
                    String.format("%.1f°", getAttackAngle(attacker, victim)));
        }

        if (reach != null && reach.isEnabled() && reach.check(attacker, victim)) {
            double distance = attacker.getLocation().distance(victim.getLocation());
            flagPlayer(attacker, reach, "Distance: " + String.format("%.2f blocks", distance));

            // Cancel the hit if reach is too far
            if (distance > 4.0) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        violations.remove(uuid);
        lastSafeLocation.remove(uuid);

        // Cleanup checks
        Check flight = getCheck("Flight");
        if (flight instanceof FlightCheck fc) {
            fc.cleanup(uuid);
        }
        Check reach = getCheck("Reach");
        if (reach instanceof ReachCheck rc) {
            rc.cleanup(uuid);
        }
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

        plugin.getDiscordManager().sendEmbed(title, description, vl >= check.getMaxViolations() ? 0xFF0000 : 0xFFAA00);
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
                // Ban implementation would go here
                break;
            case "notify":
                // Just notify, no action
                break;
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

    private double getAttackAngle(Player attacker, Player victim) {
        var toTarget = victim.getLocation().toVector().subtract(attacker.getEyeLocation().toVector()).normalize();
        var lookDir = attacker.getEyeLocation().getDirection().normalize();
        double dot = lookDir.dot(toTarget);
        return Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dot))));
    }

    private Check getCheck(String name) {
        for (Check check : checks) {
            if (check.getName().equalsIgnoreCase(name)) {
                return check;
            }
        }
        return null;
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
