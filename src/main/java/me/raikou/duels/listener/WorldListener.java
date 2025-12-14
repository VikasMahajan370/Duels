package me.raikou.duels.listener;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.arena.Arena;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.Material;
import org.bukkit.Location;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class WorldListener implements Listener {

    private final DuelsPlugin plugin;

    public WorldListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isProtectedWorld(event.getBlock().getWorld())) {
            // In lobby, block ALL breaking for non-admins
            if (isLobbyWorld(event.getBlock().getWorld())) {
                if (!event.getPlayer().hasPermission("duels.admin.bypass")) {
                    event.setCancelled(true);
                }
                return;
            }
            // In arena, allow transient blocks
            Material type = event.getBlock().getType();
            // Allow breaking transient blocks (fire, placed blocks etc.)
            if (type == Material.FIRE || type == Material.COBBLESTONE || type == Material.DIRT
                    || type == Material.SAND || type == Material.GRAVEL || type == Material.TNT) {
                return; // Allow
            }
            Player player = event.getPlayer();
            if (!player.hasPermission("duels.admin.bypass")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // Block all placement in lobby for non-admins
        if (isLobbyWorld(event.getBlock().getWorld())) {
            if (!event.getPlayer().hasPermission("duels.admin.bypass")) {
                event.setCancelled(true);
            }
        }
        // Arena: allow all placement
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (isProtectedWorld(event.getLocation().getWorld())) {
            if (event.getEntity() instanceof org.bukkit.entity.LivingEntity && !(event.getEntity() instanceof Player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onVoidDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID)
            return;

        if (isLobbyWorld(player.getWorld())) {
            event.setCancelled(true);
            Location lobby = plugin.getLobbyManager().getLobbyLocation();
            if (lobby != null) {
                player.teleport(lobby);
            }
        } else if (isArenaWorld(player.getWorld())) {
            // In duel, void = death (let it happen)
        }
    }

    // Advancements are disabled via gamerule in world creation

    private boolean isProtectedWorld(World world) {
        return isArenaWorld(world) || isLobbyWorld(world);
    }

    private boolean isLobbyWorld(World world) {
        Location lobby = plugin.getLobbyManager().getLobbyLocation();
        if (lobby == null || lobby.getWorld() == null)
            return false;
        return lobby.getWorld().getName().equals(world.getName());
    }

    private boolean isArenaWorld(World world) {
        String name = world.getName();
        // Check duel instances
        if (name.startsWith("duel_")) {
            return true;
        }

        // Check template worlds
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.getSpawn1() != null && arena.getSpawn1().getWorld().getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        String msg = "<green><bold>[+]</bold></green> <gray>" + event.getPlayer().getName() + "</gray>";
        event.joinMessage(MiniMessage.miniMessage().deserialize(msg));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String msg = "<red><bold>[-]</bold></red> <gray>" + event.getPlayer().getName() + "</gray>";
        event.quitMessage(MiniMessage.miniMessage().deserialize(msg));
    }
}
