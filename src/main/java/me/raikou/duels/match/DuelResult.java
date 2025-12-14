package me.raikou.duels.match;

import lombok.Builder;
import lombok.Getter;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Stores comprehensive data about a completed duel match.
 */
@Getter
@Builder
public class DuelResult {

    private final String matchId; // Unique match ID for identification
    private final long timestamp; // When the match ended
    private final String kitName; // Kit used in the match
    private final boolean ranked; // Was it a ranked match?

    // Winner data
    private final UUID winnerUuid;
    private final String winnerName;
    private final double winnerHealth; // Health at end of match
    private final int winnerEloChange; // ELO change (positive)
    private final int winnerNewElo; // New ELO after match
    private final List<ItemStack> winnerInventory; // Winner's inventory at end
    private final ItemStack[] winnerArmor; // Winner's armor at end

    // Loser data
    private final UUID loserUuid;
    private final String loserName;
    private final int loserEloChange; // ELO change (negative)
    private final int loserNewElo; // New ELO after match
    private final List<ItemStack> loserInventory; // Loser's inventory at death
    private final ItemStack[] loserArmor; // Loser's armor at death

    // Match duration
    private final long durationMs; // Match duration in milliseconds

    /**
     * Get formatted match duration as string (e.g., "1:23").
     */
    public String getFormattedDuration() {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * Get winner's health formatted as hearts.
     */
    public String getFormattedWinnerHealth() {
        double hearts = winnerHealth / 2.0;
        return String.format("%.1f ❤", hearts);
    }

    /**
     * Get short match ID for display (first 7 characters).
     */
    public String getShortMatchId() {
        return matchId.length() > 7 ? matchId.substring(0, 7) : matchId;
    }
}
