package me.raikou.duels.combat;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles shield prevention for 1.8 PvP gameplay.
 * 
 * Features:
 * - Prevents shield usage (right-click blocking)
 * - Prevents equipping shields to offhand
 * - Prevents swapping items to offhand if shield
 */
public class ShieldHandler implements Listener {

    private final CombatManager manager;

    public ShieldHandler(CombatManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShieldUse(PlayerInteractEvent event) {
        if (!manager.isEnabled() || !manager.isDisableShields())
            return;

        Player player = event.getPlayer();

        // Only in duels
        if (!manager.isInDuel(player))
            return;

        // Check for right-click with shield
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Check main hand
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand != null && mainHand.getType() == Material.SHIELD) {
            event.setCancelled(true);
            return;
        }

        // Check offhand
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && offHand.getType() == Material.SHIELD) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (!manager.isEnabled() || !manager.isDisableShields())
            return;

        Player player = event.getPlayer();

        // Only in duels
        if (!manager.isInDuel(player))
            return;

        // Prevent swapping if either item is a shield
        ItemStack mainHand = event.getMainHandItem();
        ItemStack offHand = event.getOffHandItem();

        if ((mainHand != null && mainHand.getType() == Material.SHIELD) ||
                (offHand != null && offHand.getType() == Material.SHIELD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!manager.isEnabled() || !manager.isDisableShields())
            return;

        if (!(event.getWhoClicked() instanceof Player player))
            return;

        // Only in duels
        if (!manager.isInDuel(player))
            return;

        // Check if trying to put shield in offhand slot
        if (event.getSlotType() == InventoryType.SlotType.QUICKBAR) {
            // Offhand slot is slot 40
            if (event.getRawSlot() == 45 || event.getSlot() == 40) {
                ItemStack cursor = event.getCursor();
                ItemStack current = event.getCurrentItem();

                if ((cursor != null && cursor.getType() == Material.SHIELD) ||
                        (current != null && current.getType() == Material.SHIELD)) {
                    event.setCancelled(true);
                }
            }
        }

        // Also prevent shift-clicking shields
        if (event.isShiftClick()) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() == Material.SHIELD) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Remove any shields from a player's inventory (called when entering duel).
     */
    public void removeShields(Player player) {
        if (!manager.isDisableShields())
            return;

        // Check offhand
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && offHand.getType() == Material.SHIELD) {
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        }

        // Check entire inventory
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.SHIELD) {
                player.getInventory().setItem(i, new ItemStack(Material.AIR));
            }
        }
    }
}
