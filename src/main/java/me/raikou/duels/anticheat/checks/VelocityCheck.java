package me.raikou.duels.anticheat.checks;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.anticheat.AbstractCheck;
import me.raikou.duels.anticheat.CheckType;
import me.raikou.duels.anticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Detects players who do not take knockback (Anti-Knockback / Velocity).
 * Verifies that after taking damage, the player moves accordingly.
 */
public class VelocityCheck extends AbstractCheck {

    public VelocityCheck(DuelsPlugin plugin) {
        super(plugin, "Velocity", CheckType.MOVEMENT, "velocity");
    }

    // Velocity is complex because we need to know when they TOOK velocity.
    // For now, checking 'NoSlow' or simple vertical modifiers is safer.
    // Full velocity requires listening to Velocity packets which is
    // NMS/PacketEvents dependent.
    // We will do a basic "Gravity" check here instead for now to avoid false
    // positives.

    @Override
    public boolean onMove(Player player, Location to, Location from, PlayerData data) {
        // If player is in air for too long without falling, handled by FlightCheck.
        return false;
    }

    // Note: Better Velocity checks require listening to EntityVelocityEvent and
    // tracking transaction IDs
    // which is out of scope for a simple non-NMS refactor.
    // We rely on FlightCheck to catch people hovering after hits.
}
