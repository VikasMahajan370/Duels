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

public class WorldListener implements Listener {

    private final DuelsPlugin plugin;

    public WorldListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isArenaWorld(event.getBlock().getWorld())) {
            Player player = event.getPlayer();
            if (!player.hasPermission("duels.admin.bypass")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isArenaWorld(event.getBlock().getWorld())) {
            Player player = event.getPlayer();
            if (!player.hasPermission("duels.admin.bypass")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (isArenaWorld(event.getLocation().getWorld())) {
            // Allow custom spawns or plugins, but maybe cancel natural
            // For now, cancel all non-player spawns if we want strict arena
            // Actually, duels might need arrows (projectiles are entities).
            // We should only cancel living entities that are not players.

            if (event.getEntity() instanceof org.bukkit.entity.LivingEntity && !(event.getEntity() instanceof Player)) {
                // Check spawn reason if possible, but 1.8-1.21 API varies.
                // Simplest is to cancel all mobs.
                event.setCancelled(true);
            }
        }
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
}
