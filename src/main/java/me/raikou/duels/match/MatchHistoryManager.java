package me.raikou.duels.match;

import me.raikou.duels.DuelsPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages match history and provides access to recent match results.
 * Stores the last N matches in memory for quick GUI access.
 */
public class MatchHistoryManager {

    private final DuelsPlugin plugin;
    private final Map<String, DuelResult> matchHistory = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerLastMatch = new ConcurrentHashMap<>(); // Player -> Last match ID
    private final int maxHistorySize;

    public MatchHistoryManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.maxHistorySize = plugin.getConfig().getInt("match-history.max-size", 100);
    }

    /**
     * Store a new match result.
     */
    public void addMatch(DuelResult result) {
        matchHistory.put(result.getMatchId(), result);
        playerLastMatch.put(result.getWinnerUuid(), result.getMatchId());
        playerLastMatch.put(result.getLoserUuid(), result.getMatchId());

        // Clean up old matches if over limit
        if (matchHistory.size() > maxHistorySize) {
            // Remove oldest entries (simple approach: remove first found)
            long oldestTime = Long.MAX_VALUE;
            String oldestId = null;
            for (Map.Entry<String, DuelResult> entry : matchHistory.entrySet()) {
                if (entry.getValue().getTimestamp() < oldestTime) {
                    oldestTime = entry.getValue().getTimestamp();
                    oldestId = entry.getKey();
                }
            }
            if (oldestId != null) {
                matchHistory.remove(oldestId);
            }
        }

        plugin.getLogger().info("[Match] Stored match result: " + result.getShortMatchId());
    }

    /**
     * Get a match result by ID.
     */
    public DuelResult getMatch(String matchId) {
        return matchHistory.get(matchId);
    }

    /**
     * Get a player's last match result ID.
     */
    public String getPlayerLastMatchId(UUID playerUuid) {
        return playerLastMatch.get(playerUuid);
    }

    /**
     * Get a player's last match result.
     */
    public DuelResult getPlayerLastMatch(UUID playerUuid) {
        String matchId = playerLastMatch.get(playerUuid);
        return matchId != null ? matchHistory.get(matchId) : null;
    }

    /**
     * Check if a match exists.
     */
    public boolean hasMatch(String matchId) {
        return matchHistory.containsKey(matchId);
    }

    /**
     * Get total stored matches count.
     */
    public int getStoredMatchCount() {
        return matchHistory.size();
    }
}
