package me.raikou.duels.anticheat;

import org.bukkit.entity.Player;

/**
 * Interface for all anti-cheat checks.
 * Each check should implement this interface.
 */
public interface Check {

    /**
     * Get the name of this check
     */
    String getName();

    /**
     * Get the check type/category
     */
    CheckType getType();

    /**
     * Check if this check is enabled
     */
    boolean isEnabled();

    /**
     * Called when a player moves.
     */
    default boolean onMove(Player player, org.bukkit.Location to, org.bukkit.Location from,
            me.raikou.duels.anticheat.data.PlayerData data) {
        return false;
    }

    /**
     * Called when a player attacks another entity.
     */
    default boolean onAttack(Player attacker, org.bukkit.entity.Entity victim,
            me.raikou.duels.anticheat.data.PlayerData data) {
        return false;
    }

    /**
     * Get the maximum violations before action
     */
    int getMaxViolations();
}
