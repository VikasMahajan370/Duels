package me.raikou.duels.queue;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.kit.Kit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class QueueManager {

    private final DuelsPlugin plugin;
    // QueueType -> KitName -> List of Players (for SOLO/TEAM)
    private final Map<QueueType, Map<String, LinkedList<UUID>>> queues = new HashMap<>();
    // Ranked queue: KitName -> List of RankedQueueEntry
    private final Map<String, List<RankedQueueEntry>> rankedQueues = new ConcurrentHashMap<>();

    // Config values
    private int eloRangeInitial = 100;
    private int eloRangeExpansion = 50;
    private int eloRangeMax = 500;

    public QueueManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        for (QueueType type : QueueType.values()) {
            queues.put(type, new HashMap<>());
        }
        loadConfig();
        startRankedMatchmakingTask();
    }

    private void loadConfig() {
        eloRangeInitial = plugin.getConfig().getInt("ranked.elo-range-initial", 100);
        eloRangeExpansion = plugin.getConfig().getInt("ranked.elo-range-expansion", 50);
        eloRangeMax = plugin.getConfig().getInt("ranked.elo-range-max", 500);
    }

    public void addToQueue(Player player, String kitName, QueueType type) {
        if (isInQueue(player)) {
            me.raikou.duels.util.MessageUtil.sendError(player, "queue.already-in");
            return;
        }

        Kit kit = plugin.getKitManager().getKit(kitName);
        if (kit == null) {
            me.raikou.duels.util.MessageUtil.sendError(player, "duel.kit-not-found", "%kit%", kitName);
            return;
        }

        if (type == QueueType.RANKED) {
            addToRankedQueue(player, kitName);
            return;
        }

        queues.get(type).computeIfAbsent(kitName, k -> new LinkedList<>()).add(player.getUniqueId());
        me.raikou.duels.util.MessageUtil.sendSuccess(player, "queue.join", "%kit%", kitName, "%type%", type.name());
        me.raikou.duels.util.MessageUtil.sendInfo(player, "queue.info", "%count%",
                String.valueOf(queues.get(type).get(kitName).size()), "%required%",
                String.valueOf(type.getRequiredPlayers()));

        giveQueueItems(player);
        checkQueue(kitName, type);
    }

    private void addToRankedQueue(Player player, String kitName) {
        // Load ELO async then add to queue
        plugin.getStorage().loadElo(player.getUniqueId(), kitName).thenAccept(elo -> {
            RankedQueueEntry entry = new RankedQueueEntry(player.getUniqueId(), kitName, elo);
            rankedQueues.computeIfAbsent(kitName, k -> Collections.synchronizedList(new ArrayList<>())).add(entry);

            Bukkit.getScheduler().runTask(plugin, () -> {
                me.raikou.duels.util.MessageUtil.sendSuccess(player, "queue.join", "%kit%", kitName, "%type%",
                        "RANKED");
                me.raikou.duels.util.MessageUtil.sendInfo(player, "ranked.queue-info", "%elo%", String.valueOf(elo));
                giveQueueItems(player);
            });
        });
    }

    public void removeFromQueue(Player player) {
        // Remove from regular queues
        for (QueueType type : QueueType.values()) {
            if (type == QueueType.RANKED)
                continue;
            for (List<UUID> list : queues.get(type).values()) {
                if (list.remove(player.getUniqueId())) {
                    me.raikou.duels.util.MessageUtil.sendSuccess(player, "queue.leave");
                    restoreLobbyItems(player);
                    return;
                }
            }
        }

        // Remove from ranked queues
        for (List<RankedQueueEntry> entries : rankedQueues.values()) {
            if (entries.removeIf(e -> e.getPlayer().equals(player.getUniqueId()))) {
                me.raikou.duels.util.MessageUtil.sendSuccess(player, "queue.leave");
                restoreLobbyItems(player);
                return;
            }
        }

        me.raikou.duels.util.MessageUtil.sendError(player, "queue.not-in");
    }

    private void checkQueue(String kitName, QueueType type) {
        LinkedList<UUID> queue = queues.get(type).get(kitName);
        if (queue == null)
            return;

        if (queue.size() >= type.getRequiredPlayers()) {
            if (type == QueueType.SOLO) {
                UUID uuid1 = queue.poll();
                UUID uuid2 = queue.poll();

                if (uuid1 != null && uuid2 != null) {
                    Player p1 = Bukkit.getPlayer(uuid1);
                    Player p2 = Bukkit.getPlayer(uuid2);

                    if (p1 != null && p2 != null) {
                        plugin.getDuelManager().startDuel(p1, p2, kitName, false);
                    } else {
                        if (p1 != null)
                            addToQueue(p1, kitName, type);
                        if (p2 != null)
                            addToQueue(p2, kitName, type);
                    }
                }
            }
        }
    }

    private void startRankedMatchmakingTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                checkRankedQueues();
            }
        }.runTaskTimer(plugin, 20L, 20L); // Every second
    }

    private void checkRankedQueues() {
        for (Map.Entry<String, List<RankedQueueEntry>> entry : rankedQueues.entrySet()) {
            String kitName = entry.getKey();
            List<RankedQueueEntry> queue = entry.getValue();

            if (queue.size() < 2)
                continue;

            // Sort by ELO for better matching
            List<RankedQueueEntry> sorted = new ArrayList<>(queue);
            sorted.sort(Comparator.comparingInt(RankedQueueEntry::getElo));

            for (int i = 0; i < sorted.size() - 1; i++) {
                RankedQueueEntry e1 = sorted.get(i);
                RankedQueueEntry e2 = sorted.get(i + 1);

                if (e1.canMatchWith(e2, eloRangeInitial, eloRangeExpansion, eloRangeMax)) {
                    // Match found!
                    queue.remove(e1);
                    queue.remove(e2);

                    Player p1 = Bukkit.getPlayer(e1.getPlayer());
                    Player p2 = Bukkit.getPlayer(e2.getPlayer());

                    if (p1 != null && p2 != null) {
                        plugin.getDuelManager().startDuel(p1, p2, kitName, true);
                    }
                    break; // Only one match per tick to be safe
                }
            }
        }
    }

    public boolean isInQueue(Player player) {
        for (QueueType type : QueueType.values()) {
            if (type == QueueType.RANKED)
                continue;
            for (List<UUID> list : queues.get(type).values()) {
                if (list.contains(player.getUniqueId())) {
                    return true;
                }
            }
        }

        for (List<RankedQueueEntry> entries : rankedQueues.values()) {
            for (RankedQueueEntry e : entries) {
                if (e.getPlayer().equals(player.getUniqueId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public int getQueueSize(String kitName) {
        LinkedList<UUID> list = queues.get(QueueType.SOLO).get(kitName);
        return list != null ? list.size() : 0;
    }

    public int getRankedQueueSize(String kitName) {
        List<RankedQueueEntry> list = rankedQueues.get(kitName);
        return list != null ? list.size() : 0;
    }

    private void giveQueueItems(Player player) {
        player.getInventory().clear();

        // Leave Queue item - Red Dye
        org.bukkit.inventory.ItemStack leaveItem = new org.bukkit.inventory.ItemStack(org.bukkit.Material.RED_DYE);
        org.bukkit.inventory.meta.ItemMeta meta = leaveItem.getItemMeta();
        if (meta != null) {
            meta.displayName(me.raikou.duels.util.MessageUtil
                    .parse("<red><bold>Leave Queue</bold></red> <dark_gray>(Right Click)"));
            leaveItem.setItemMeta(meta);
        }
        player.getInventory().setItem(4, leaveItem);
    }

    private void restoreLobbyItems(Player player) {
        plugin.getLobbyManager().giveLobbyItems(player);
    }
}
