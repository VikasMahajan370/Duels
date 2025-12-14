package me.raikou.duels.combat;

import me.raikou.duels.duel.Duel;
import me.raikou.duels.kit.Kit;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Custom knockback engine mimicking Minecraft 1.8 behavior.
 * 
 * Features:
 * - Consistent horizontal knockback (~0.34-0.38)
 * - Consistent vertical knockback (~0.30-0.34)
 * - Sprint-reset on hit (for W-tap/blockhit combos)
 * - Knockback enchantment support
 * - Reduced randomness compared to vanilla
 * - Direction based on attacker's position
 * - Per-kit knockback presets
 */
public class KnockbackHandler implements Listener {

    private final CombatManager manager;

    public KnockbackHandler(CombatManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!manager.isEnabled())
            return;

        if (!(event.getDamager() instanceof Player attacker))
            return;
        if (!(event.getEntity() instanceof Player victim))
            return;

        // Only apply in duels
        if (!manager.isInDuel(attacker))
            return;

        // Apply custom knockback on next tick (after vanilla KB is applied)
        Bukkit.getScheduler().runTask(manager.getPlugin(), () -> {
            applyKnockback(attacker, victim);
        });
    }

    /**
     * Apply 1.8-style knockback to the victim.
     * Uses per-kit knockback settings if available.
     * 
     * @param attacker The attacking player
     * @param victim   The player receiving knockback
     */
    public void applyKnockback(Player attacker, Player victim) {
        // Calculate direction from attacker to victim
        Vector direction = victim.getLocation().toVector()
                .subtract(attacker.getLocation().toVector());
        direction.setY(0); // Ignore vertical component for direction

        // Normalize direction
        if (direction.lengthSquared() > 0) {
            direction.normalize();
        } else {
            // Fallback: use attacker's facing direction
            direction = attacker.getLocation().getDirection();
            direction.setY(0);
            if (direction.lengthSquared() > 0) {
                direction.normalize();
            }
        }

        // Get per-kit knockback values or use defaults
        double horizontal = manager.getHorizontalKB();
        double vertical = manager.getVerticalKB();

        Kit kit = getKitForPlayer(attacker);
        if (kit != null && kit.hasCustomKnockback()) {
            if (kit.getHorizontalKB() > 0) {
                horizontal = kit.getHorizontalKB();
            }
            if (kit.getVerticalKB() > 0) {
                vertical = kit.getVerticalKB();
            }
        }

        // Sprint bonus
        if (attacker.isSprinting()) {
            horizontal *= (1.0 + manager.getSprintMultiplier());
        }

        // Knockback enchantment bonus
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon != null) {
            int kbLevel = weapon.getEnchantmentLevel(Enchantment.KNOCKBACK);
            if (kbLevel > 0) {
                horizontal += kbLevel * manager.getKbEnchantMultiplier();
            }
        }

        // Calculate final velocity
        Vector knockback = direction.multiply(horizontal);
        knockback.setY(vertical);

        // Apply velocity directly (overriding vanilla knockback)
        victim.setVelocity(knockback);

        // Sprint reset - crucial for 1.8 combo mechanics
        // This forces the attacker to re-sprint between hits
        if (attacker.isSprinting()) {
            attacker.setSprinting(false);
        }
    }

    /**
     * Get the kit being used in the player's current duel.
     */
    private Kit getKitForPlayer(Player player) {
        Duel duel = manager.getPlugin().getDuelManager().getDuel(player);
        if (duel == null)
            return null;

        String kitName = duel.getKitName();
        if (kitName == null)
            return null;

        return manager.getPlugin().getKitManager().getKit(kitName);
    }

    /**
     * Get per-kit rod knockback values.
     * Returns default values if kit has no custom settings.
     */
    public double[] getRodKnockbackForPlayer(Player player) {
        // Use defaults from config
        double horizontal = manager.getRodHorizontalKB();
        double vertical = manager.getRodVerticalKB();

        Kit kit = getKitForPlayer(player);
        if (kit != null && kit.hasCustomRodKnockback()) {
            if (kit.getRodHorizontalKB() > 0) {
                horizontal = kit.getRodHorizontalKB();
            }
            if (kit.getRodVerticalKB() > 0) {
                vertical = kit.getRodVerticalKB();
            }
        }

        return new double[] { horizontal, vertical };
    }

    /**
     * Apply knockback in a specific direction (for fishing rod, etc.)
     * 
     * @param victim     The player receiving knockback
     * @param direction  The direction to knock back
     * @param horizontal Horizontal strength
     * @param vertical   Vertical strength
     */
    public void applyDirectionalKnockback(Player victim, Vector direction, double horizontal, double vertical) {
        direction.setY(0);
        if (direction.lengthSquared() > 0) {
            direction.normalize();
        }

        Vector knockback = direction.multiply(horizontal);
        knockback.setY(vertical);

        victim.setVelocity(knockback);
    }

    /**
     * Get the current horizontal knockback value.
     */
    public double getHorizontalKB() {
        return manager.getHorizontalKB();
    }

    /**
     * Get the current vertical knockback value.
     */
    public double getVerticalKB() {
        return manager.getVerticalKB();
    }
}
