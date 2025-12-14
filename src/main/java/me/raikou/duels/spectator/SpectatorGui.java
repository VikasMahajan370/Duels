package me.raikou.duels.spectator;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.duel.Duel;
import me.raikou.duels.duel.DuelState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * GUI for displaying and selecting active duels to spectate.
 */
public class SpectatorGui implements Listener {

    private final DuelsPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Track which duel each slot corresponds to for click handling
    private final Map<UUID, Map<Integer, Duel>> playerSlotDuelMap = new HashMap<>();

    public SpectatorGui(DuelsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Get the GUI title from language file.
     */
    private Component getGuiTitle() {
        String titleRaw = plugin.getLanguageManager().getMessage("gui.spectator.title");
        return miniMessage.deserialize(titleRaw);
    }

    /**
     * Open the spectator GUI for a player.
     * Shows all active duels with details.
     */
    public void openGui(Player player) {
        int size = 54;
        Inventory inv = Bukkit.createInventory(null, size, getGuiTitle());

        // Fill with dark gray glass pane border
        ItemStack borderPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = borderPane.getItemMeta();
        if (borderMeta != null) {
            borderMeta.displayName(Component.text(" "));
            borderPane.setItemMeta(borderMeta);
        }

        for (int i = 0; i < size; i++) {
            inv.setItem(i, borderPane);
        }

        // Get active duels
        Set<Duel> activeDuels = plugin.getDuelManager().getActiveDuels();
        List<Duel> fightingDuels = new ArrayList<>();

        for (Duel duel : activeDuels) {
            if (duel.getState() == DuelState.FIGHTING) {
                fightingDuels.add(duel);
            }
        }

        // Slots for duel items (center area)
        int[] duelSlots = { 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34 };

        Map<Integer, Duel> slotDuelMap = new HashMap<>();

        if (fightingDuels.isEmpty()) {
            // No active duels - show info item
            ItemStack noduels = new ItemStack(Material.BARRIER);
            ItemMeta noduelsMeta = noduels.getItemMeta();
            if (noduelsMeta != null) {
                String noduelsMsg = plugin.getLanguageManager().getMessage("gui.spectator.no-duels");
                noduelsMeta.displayName(miniMessage.deserialize(noduelsMsg));
                noduels.setItemMeta(noduelsMeta);
            }
            inv.setItem(22, noduels);
        } else {
            int index = 0;
            for (Duel duel : fightingDuels) {
                if (index >= duelSlots.length)
                    break;

                int slot = duelSlots[index];
                ItemStack duelItem = createDuelItem(duel);
                inv.setItem(slot, duelItem);
                slotDuelMap.put(slot, duel);
                index++;
            }
        }

        // Store slot mapping for this player
        playerSlotDuelMap.put(player.getUniqueId(), slotDuelMap);

        // Close button at bottom
        ItemStack closeBtn = new ItemStack(Material.ARROW);
        ItemMeta closeMeta = closeBtn.getItemMeta();
        if (closeMeta != null) {
            closeMeta.displayName(miniMessage.deserialize("<red>Close</red>"));
            closeBtn.setItemMeta(closeMeta);
        }
        inv.setItem(49, closeBtn);

        player.openInventory(inv);
    }

    /**
     * Create an item representing a duel.
     */
    private ItemStack createDuelItem(Duel duel) {
        // Get player info
        UUID player1Uuid = duel.getPlayers().get(0);
        UUID player2Uuid = duel.getPlayers().get(1);

        Player player1 = Bukkit.getPlayer(player1Uuid);
        Player player2 = Bukkit.getPlayer(player2Uuid);

        String player1Name = player1 != null ? player1.getName() : "Unknown";
        String player2Name = player2 != null ? player2.getName() : "Unknown";

        // Create player head
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();

        if (skullMeta != null && player1 != null) {
            skullMeta.setOwningPlayer(player1);
        }

        // Set display name
        String nameFormat = plugin.getLanguageManager().getMessage("gui.spectator.item-name");
        nameFormat = nameFormat.replace("%player1%", player1Name).replace("%player2%", player2Name);

        if (skullMeta != null) {
            skullMeta.displayName(miniMessage.deserialize(nameFormat));

            // Build lore
            List<Component> lore = new ArrayList<>();

            // Calculate duration
            long durationMs = System.currentTimeMillis() - duel.getStartTime();
            String duration = formatDuration(durationMs);

            // Get spectator count
            int spectatorCount = duel.getSpectators().size();

            for (String line : plugin.getLanguageManager().getList("gui.spectator.lore")) {
                line = line.replace("%kit%", duel.getKitName())
                        .replace("%arena%", duel.getArena().getName())
                        .replace("%duration%", duration)
                        .replace("%spectators%", String.valueOf(spectatorCount));
                lore.add(miniMessage.deserialize(line));
            }

            skullMeta.lore(lore);
            head.setItemMeta(skullMeta);
        }

        return head;
    }

    /**
     * Format duration from milliseconds to readable format.
     */
    private String formatDuration(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        }
        return String.format("%ds", seconds);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().title().equals(getGuiTitle())) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        int slot = event.getRawSlot();

        // Close button
        if (slot == 49 && clicked.getType() == Material.ARROW) {
            player.closeInventory();
            return;
        }

        // Check if clicked slot has a duel
        Map<Integer, Duel> slotMap = playerSlotDuelMap.get(player.getUniqueId());
        if (slotMap == null) {
            return;
        }

        Duel duel = slotMap.get(slot);
        if (duel == null) {
            return;
        }

        // Close inventory first
        player.closeInventory();

        // Start spectating
        plugin.getSpectatorManager().startSpectating(player, duel);
    }

    /**
     * Clean up when player closes inventory.
     */
    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getView().title().equals(getGuiTitle())) {
            playerSlotDuelMap.remove(event.getPlayer().getUniqueId());
        }
    }
}
