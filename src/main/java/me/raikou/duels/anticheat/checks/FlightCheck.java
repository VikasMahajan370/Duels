package me.raikou.duels.anticheat.checks;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.anticheat.AbstractCheck;
import me.raikou.duels.anticheat.CheckType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

/**
 * Strict flight detection with multiple detection methods:
 * - Air time tracking (hovering detection)
 * - Vertical velocity analysis (illegal upward movement)
 * - Ground distance checking
 * - Y-level stability detection (hovering in place)
 */
public class FlightCheck extends AbstractCheck {

    // Tracking data
    private final Map<UUID, Integer> airTicks = new HashMap<>();
    private final Map<UUID, LinkedList<Double>> yHistory = new HashMap<>();
    private final Map<UUID, Long> lastOnGround = new HashMap<>();
    private final Map<UUID, Double> lastY = new HashMap<>();
    private final Map<UUID, Integer> hoverTicks = new HashMap<>();

    // Thresholds - STRICT
    private static final int MAX_AIR_TICKS = 35; // ~1.75 seconds max air time
    private static final int MAX_HOVER_TICKS = 12; // ~0.6 seconds hovering
    private static final double HOVER_THRESHOLD = 0.05; // Y change considered "hovering"
    private static final double MAX_UPWARD_VELOCITY = 0.42; // Normal jump is ~0.42
    private static final int Y_HISTORY_SIZE = 5;

    public FlightCheck(DuelsPlugin plugin) {
        super(plugin, "Flight", CheckType.MOVEMENT, "flight");
    }

    @Override
    public boolean check(Player player, Object... data) {
        if (!isEnabled())
            return false;

        UUID uuid = player.getUniqueId();

        // === EXEMPTIONS ===

        // Creative/Spectator mode
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            reset(uuid);
            return false;
        }

        // Allowed flight (fly command, etc.)
        if (player.getAllowFlight() || player.isFlying()) {
            reset(uuid);
            return false;
        }

        // Potion effects that allow flight-like behavior
        if (player.hasPotionEffect(PotionEffectType.LEVITATION) ||
                player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            reset(uuid);
            return false;
        }

        // Riptide trident propulsion check
        if (player.isRiptiding()) {
            reset(uuid);
            return false;
        }

        // In water, lava, or climbing
        if (player.isInWater() || isInLiquid(player) || isClimbing(player) || player.isGliding()) {
            reset(uuid);
            return false;
        }

        // In vehicle
        if (player.isInsideVehicle()) {
            reset(uuid);
            return false;
        }

        // Recently teleported or respawned (grace period)
        // Skip if player just joined
        if (!lastY.containsKey(uuid)) {
            lastY.put(uuid, player.getLocation().getY());
            return false;
        }

        // === DETECTION ===

        Location loc = player.getLocation();
        double currentY = loc.getY();
        double previousY = lastY.getOrDefault(uuid, currentY);
        double yDiff = currentY - previousY;
        lastY.put(uuid, currentY);

        // Track Y history for pattern analysis
        LinkedList<Double> history = yHistory.computeIfAbsent(uuid, k -> new LinkedList<>());
        history.addLast(yDiff);
        if (history.size() > Y_HISTORY_SIZE) {
            history.removeFirst();
        }

        // Check if player is on ground
        boolean onGround = isOnGroundStrict(player);

        if (onGround) {
            lastOnGround.put(uuid, System.currentTimeMillis());
            reset(uuid);
            return false;
        }

        // === AIR TICK TRACKING ===
        int currentAirTicks = airTicks.getOrDefault(uuid, 0) + 1;
        airTicks.put(uuid, currentAirTicks);

        // === DETECTION 1: Too long in air ===
        if (currentAirTicks > MAX_AIR_TICKS) {
            // Check if not near any blocks (truly flying)
            if (!hasBlocksNearby(player, 2)) {
                return true; // FLYING
            }
        }

        // === DETECTION 2: Hovering (Y not changing while in air) ===
        if (Math.abs(yDiff) < HOVER_THRESHOLD) {
            int hover = hoverTicks.getOrDefault(uuid, 0) + 1;
            hoverTicks.put(uuid, hover);

            if (hover > MAX_HOVER_TICKS && !hasBlocksNearby(player, 1)) {
                // Player is hovering in mid-air
                return true; // HOVERING
            }
        } else {
            hoverTicks.put(uuid, 0);
        }

        // === DETECTION 3: Illegal upward movement ===
        // After 15 ticks in air, player should be falling, not rising
        if (currentAirTicks > 15 && yDiff > 0.1) {
            // Still going UP after normal jump apex
            if (!hasBlocksNearby(player, 1)) {
                return true; // FLIGHT HACK
            }
        }

        // === DETECTION 4: Consistent Y-level (flying straight) ===
        if (history.size() >= Y_HISTORY_SIZE && currentAirTicks > 20) {
            boolean allSmall = true;
            for (double diff : history) {
                if (Math.abs(diff) > 0.1) {
                    allSmall = false;
                    break;
                }
            }
            if (allSmall && !hasBlocksNearby(player, 2)) {
                // Y hasn't changed significantly while in air for extended time
                return true; // FLYING STRAIGHT
            }
        }

        // === DETECTION 5: Impossible upward velocity ===
        if (yDiff > MAX_UPWARD_VELOCITY + 0.1 && currentAirTicks > 5) {
            // Moving up faster than possible without cheats
            return true; // SPEED/FLIGHT HACK
        }

        return false;
    }

    private void reset(UUID uuid) {
        airTicks.remove(uuid);
        hoverTicks.remove(uuid);
        // Keep yHistory and lastY for better tracking
    }

    /**
     * Strict ground check - checks multiple points below player
     */
    private boolean isOnGroundStrict(Player player) {
        Location loc = player.getLocation();

        // Check at foot level and slightly below
        for (double x = -0.3; x <= 0.3; x += 0.3) {
            for (double z = -0.3; z <= 0.3; z += 0.3) {
                for (double y = -0.1; y >= -0.6; y -= 0.25) {
                    Block block = loc.clone().add(x, y, z).getBlock();
                    if (block.getType().isSolid()) {
                        return true;
                    }
                }
            }
        }

        // Also check Bukkit's ground state
        return player.isOnGround();
    }

    /**
     * Check if there are any solid blocks nearby
     */
    private boolean hasBlocksNearby(Player player, int radius) {
        Location loc = player.getLocation();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = loc.clone().add(x, y, z).getBlock();
                    if (block.getType().isSolid()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isInLiquid(Player player) {
        Location loc = player.getLocation();
        Material type = loc.getBlock().getType();
        Material below = loc.clone().subtract(0, 0.3, 0).getBlock().getType();
        return type == Material.WATER || type == Material.LAVA ||
                below == Material.WATER || below == Material.LAVA ||
                type == Material.BUBBLE_COLUMN;
    }

    private boolean isClimbing(Player player) {
        Material type = player.getLocation().getBlock().getType();
        return type == Material.LADDER || type == Material.VINE ||
                type == Material.WEEPING_VINES || type == Material.TWISTING_VINES ||
                type == Material.WEEPING_VINES_PLANT || type == Material.TWISTING_VINES_PLANT ||
                type == Material.SCAFFOLDING || type == Material.POWDER_SNOW;
    }

    public void cleanup(UUID uuid) {
        reset(uuid);
        yHistory.remove(uuid);
        lastY.remove(uuid);
        lastOnGround.remove(uuid);
    }
}
