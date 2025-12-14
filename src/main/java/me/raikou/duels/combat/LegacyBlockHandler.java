package me.raikou.duels.combat;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements fake sword blocking mechanic for 1.8 PvP feel.
 * 
 * Since the real 1.8 blocking animation cannot be restored (client-side),
 * this handler provides:
 * - Damage reduction when right-clicking with a sword
 * - Minimal cooldown between blocks
 * - Auto-release after duration or on attack
 * 
 * NOTE: This is purely mechanical - no visual sword block animation is
 * possible.
 */
public class LegacyBlockHandler implements Listener {

    private final CombatManager manager;

    // Track players currently blocking
    private final Map<UUID, Long> blockingPlayers = new ConcurrentHashMap<>();

    // Track block cooldowns
    private final Map<UUID, Long> blockCooldowns = new ConcurrentHashMap<>();

    // Track auto-release tasks
    private final Map<UUID, BukkitTask> releaseTasks = new ConcurrentHashMap<>();

    // Set of sword materials for quick lookup
    private static final Set<Material> SWORDS = Set.of(
            Material.DIAMOND_SWORD,
            Material.IRON_SWORD,
            Material.GOLDEN_SWORD,
            Material.STONE_SWORD,
            Material.WOODEN_SWORD,
            Material.NETHERITE_SWORD);

    public LegacyBlockHandler(CombatManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (!manager.isEnabled() || !manager.isSwordBlockEnabled())
            return;

        Player player = event.getPlayer();

        // Only in duels
        if (!manager.isInDuel(player))
            return;

        // Check for right-click
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !isSword(item.getType()))
            return;

        // Check cooldown
        if (isOnCooldown(player))
            return;

        // Start blocking
        startBlocking(player);

        // Cancel the event to prevent any default action
        event.setCancelled(true);
    }

    @EventHandler
    public void onItemSwitch(PlayerItemHeldEvent event) {
        // Stop blocking when switching items
        stopBlocking(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cleanup(event.getPlayer().getUniqueId());
    }

    /**
     * Check if a material is a sword.
     */
    public static boolean isSword(Material material) {
        return SWORDS.contains(material);
    }

    /**
     * Check if a player is currently blocking.
     */
    public boolean isBlocking(Player player) {
        Long blockTime = blockingPlayers.get(player.getUniqueId());
        if (blockTime == null)
            return false;

        // Check if block duration has expired
        long elapsed = System.currentTimeMillis() - blockTime;
        long durationMs = manager.getBlockDurationTicks() * 50L; // Convert ticks to ms

        if (elapsed > durationMs) {
            stopBlocking(player);
            return false;
        }

        return true;
    }

    /**
     * Check if a player is on block cooldown.
     */
    public boolean isOnCooldown(Player player) {
        Long cooldownEnd = blockCooldowns.get(player.getUniqueId());
        if (cooldownEnd == null)
            return false;

        if (System.currentTimeMillis() < cooldownEnd) {
            return true;
        }

        blockCooldowns.remove(player.getUniqueId());
        return false;
    }

    /**
     * Start blocking for a player.
     */
    public void startBlocking(Player player) {
        UUID uuid = player.getUniqueId();

        blockingPlayers.put(uuid, System.currentTimeMillis());

        // Play block sound (subtle feedback)
        player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.3f, 1.2f);

        // Schedule auto-release
        BukkitTask oldTask = releaseTasks.remove(uuid);
        if (oldTask != null) {
            oldTask.cancel();
        }

        BukkitTask releaseTask = Bukkit.getScheduler().runTaskLater(manager.getPlugin(), () -> {
            stopBlocking(player);
        }, manager.getBlockDurationTicks());

        releaseTasks.put(uuid, releaseTask);
    }

    /**
     * Stop blocking for a player.
     */
    public void stopBlocking(Player player) {
        UUID uuid = player.getUniqueId();

        if (blockingPlayers.remove(uuid) != null) {
            // Apply cooldown
            long cooldownMs = manager.getBlockCooldownTicks() * 50L;
            blockCooldowns.put(uuid, System.currentTimeMillis() + cooldownMs);
        }

        // Cancel auto-release task
        BukkitTask task = releaseTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Called when a player is hit while blocking.
     * Stops blocking and applies cooldown.
     */
    public void onHitWhileBlocking(Player player) {
        stopBlocking(player);

        // Play hit-while-blocking sound
        player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK, 0.5f, 1.5f);
    }

    /**
     * Cleanup all data for a player.
     */
    public void cleanup(UUID uuid) {
        blockingPlayers.remove(uuid);
        blockCooldowns.remove(uuid);

        BukkitTask task = releaseTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }
}
