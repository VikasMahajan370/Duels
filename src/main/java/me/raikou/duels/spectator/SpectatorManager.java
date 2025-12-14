package me.raikou.duels.spectator;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.duel.Duel;
import me.raikou.duels.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Manages the spectator system for duels.
 * Handles spectator mode setup, cleanup, and restrictions.
 */
public class SpectatorManager implements Listener {

    private final DuelsPlugin plugin;

    // Track all spectators and their watched duels
    private final Map<UUID, Duel> spectatorDuelMap = new HashMap<>();

    // Store player state before spectating for restoration
    private final Map<UUID, SpectatorState> savedStates = new HashMap<>();

    public SpectatorManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Start spectating a duel.
     * 
     * @param player The player who wants to spectate
     * @param duel   The duel to spectate
     * @return true if spectating started successfully
     */
    public boolean startSpectating(Player player, Duel duel) {
        UUID uuid = player.getUniqueId();

        // Validation checks
        if (isSpectating(player)) {
            MessageUtil.sendError(player, "spectator.already-spectating");
            return false;
        }

        if (plugin.getDuelManager().isInDuel(player)) {
            MessageUtil.sendError(player, "spectator.cannot-spectate-self");
            return false;
        }

        if (plugin.getQueueManager().isInQueue(player)) {
            plugin.getQueueManager().removeFromQueue(player);
        }

        // Save current state
        SpectatorState state = new SpectatorState(
                player.getLocation().clone(),
                player.getGameMode(),
                player.getInventory().getContents().clone(),
                player.getInventory().getArmorContents().clone());
        savedStates.put(uuid, state);

        // Clear inventory and set spectator mode
        player.getInventory().clear();
        player.setGameMode(GameMode.SPECTATOR);

        // Get spectator spawn location
        Location spectatorSpawn = getSpectatorSpawn(duel);
        if (spectatorSpawn != null) {
            player.teleport(spectatorSpawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }

        // Track spectator
        spectatorDuelMap.put(uuid, duel);
        duel.addSpectator(uuid);

        // Hide spectator from duel players (redundant in spectator mode but safe)
        for (UUID duelPlayerUuid : duel.getPlayers()) {
            Player duelPlayer = Bukkit.getPlayer(duelPlayerUuid);
            if (duelPlayer != null) {
                duelPlayer.hidePlayer(plugin, player);
            }
        }

        // Get player names for message
        String player1Name = getPlayerName(duel.getPlayers().get(0));
        String player2Name = getPlayerName(duel.getPlayers().get(1));

        MessageUtil.sendSuccess(player, "spectator.started",
                "%player1%", player1Name,
                "%player2%", player2Name);

        return true;
    }

    /**
     * Stop spectating and restore player state.
     * 
     * @param player The spectator to remove
     */
    public void stopSpectating(Player player) {
        UUID uuid = player.getUniqueId();

        if (!isSpectating(player)) {
            MessageUtil.sendError(player, "spectator.not-spectating");
            return;
        }

        Duel duel = spectatorDuelMap.remove(uuid);
        if (duel != null) {
            duel.removeSpectator(uuid);
        }

        // Show player to ALL online players (not just duel players)
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.equals(player)) {
                onlinePlayer.showPlayer(plugin, player);
            }
        }

        // Restore state
        SpectatorState state = savedStates.remove(uuid);
        if (state != null) {
            player.setGameMode(state.gameMode());
            player.getInventory().setContents(state.inventoryContents());
            player.getInventory().setArmorContents(state.armorContents());
        } else {
            player.setGameMode(GameMode.SURVIVAL);
        }

        // Teleport to lobby
        if (plugin.getLobbyManager().isLobbySet()) {
            plugin.getLobbyManager().teleportToLobby(player);
            plugin.getLobbyManager().giveLobbyItems(player);
        } else {
            if (state != null) {
                player.teleport(state.location());
            }
        }

        MessageUtil.send(player, "spectator.stopped");
    }

    /**
     * Force remove all spectators from a duel (called when duel ends).
     * 
     * @param duel The duel that ended
     */
    public void removeAllFromDuel(Duel duel) {
        Set<UUID> spectators = new HashSet<>(duel.getSpectators());
        for (UUID uuid : spectators) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                // Send end message
                MessageUtil.send(player, "spectator.duel-ended");

                // Remove from tracking first
                spectatorDuelMap.remove(uuid);
                duel.removeSpectator(uuid);

                // Restore state
                SpectatorState state = savedStates.remove(uuid);
                if (state != null) {
                    player.setGameMode(state.gameMode());
                    player.getInventory().setContents(state.inventoryContents());
                    player.getInventory().setArmorContents(state.armorContents());
                } else {
                    player.setGameMode(GameMode.SURVIVAL);
                }

                // Show player to ALL online players
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    if (!onlinePlayer.equals(player)) {
                        onlinePlayer.showPlayer(plugin, player);
                    }
                }

                // Teleport to lobby
                if (plugin.getLobbyManager().isLobbySet()) {
                    plugin.getLobbyManager().teleportToLobby(player);
                    plugin.getLobbyManager().giveLobbyItems(player);
                } else if (state != null) {
                    player.teleport(state.location());
                }
            } else {
                // Player offline, just cleanup maps
                spectatorDuelMap.remove(uuid);
                savedStates.remove(uuid);
            }
        }
    }

    /**
     * Check if a player is currently spectating.
     */
    public boolean isSpectating(Player player) {
        return spectatorDuelMap.containsKey(player.getUniqueId());
    }

    /**
     * Get the duel a player is spectating.
     */
    public Duel getSpectatedDuel(Player player) {
        return spectatorDuelMap.get(player.getUniqueId());
    }

    /**
     * Get all spectators for a specific duel.
     */
    public Set<UUID> getSpectatorsFor(Duel duel) {
        Set<UUID> result = new HashSet<>();
        for (Map.Entry<UUID, Duel> entry : spectatorDuelMap.entrySet()) {
            if (entry.getValue().equals(duel)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Get spectator spawn location for a duel.
     */
    private Location getSpectatorSpawn(Duel duel) {
        // Try arena's spectator spawn first
        Location spectatorSpawn = duel.getArena().getSpectatorSpawn();
        if (spectatorSpawn != null) {
            Location loc = spectatorSpawn.clone();
            loc.setWorld(duel.getInstanceWorld());
            return loc;
        }

        // Fallback: midpoint between spawns, elevated
        Location loc1 = duel.getArena().getSpawn1().clone();
        Location loc2 = duel.getArena().getSpawn2().clone();

        double midX = (loc1.getX() + loc2.getX()) / 2;
        double midY = Math.max(loc1.getY(), loc2.getY()) + 10;
        double midZ = (loc1.getZ() + loc2.getZ()) / 2;

        return new Location(duel.getInstanceWorld(), midX, midY, midZ);
    }

    /**
     * Helper to get player name from UUID.
     */
    private String getPlayerName(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null ? player.getName() : "Unknown";
    }

    // ==================== Event Handlers ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (spectatorDuelMap.containsKey(uuid)) {
            Duel duel = spectatorDuelMap.remove(uuid);
            if (duel != null) {
                duel.removeSpectator(uuid);
            }
            savedStates.remove(uuid);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (!isSpectating(player)) {
            return;
        }

        // Allow /duel leave to exit spectator mode
        String command = event.getMessage().toLowerCase();
        if (command.startsWith("/duel leave") || command.startsWith("/duel spectate")) {
            return;
        }

        // Check if commands are allowed
        boolean allowCommands = plugin.getConfig().getBoolean("spectator.allow-commands", false);
        if (!allowCommands) {
            event.setCancelled(true);
            MessageUtil.sendError(player, "spectator.blocked-command");
        }
    }

    /**
     * Record class for storing player state before spectating.
     */
    private record SpectatorState(
            Location location,
            GameMode gameMode,
            ItemStack[] inventoryContents,
            ItemStack[] armorContents) {
    }
}
