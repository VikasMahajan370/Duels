package me.raikou.duels.combat;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Handles damage normalization to match 1.8 PvP values.
 * 
 * Features:
 * - Remove critical hit damage bonus (no 1.5x multiplier when falling)
 * - Remove sweeping edge damage
 * - Normalize weapon damage to 1.8 values
 * - Handle Sharpness enchantment like 1.8
 */
public class DamageHandler implements Listener {

    private final CombatManager manager;

    // 1.8 Base damage values for weapons
    private static final Map<Material, Double> WEAPON_DAMAGE_18 = new EnumMap<>(Material.class);

    static {
        // Swords (1.8 values)
        WEAPON_DAMAGE_18.put(Material.DIAMOND_SWORD, 7.0);
        WEAPON_DAMAGE_18.put(Material.IRON_SWORD, 6.0);
        WEAPON_DAMAGE_18.put(Material.STONE_SWORD, 5.0);
        WEAPON_DAMAGE_18.put(Material.GOLDEN_SWORD, 4.0);
        WEAPON_DAMAGE_18.put(Material.WOODEN_SWORD, 4.0);
        WEAPON_DAMAGE_18.put(Material.NETHERITE_SWORD, 8.0); // Modern, use higher value

        // Axes (1.8 values - lower than 1.9+)
        WEAPON_DAMAGE_18.put(Material.DIAMOND_AXE, 6.0);
        WEAPON_DAMAGE_18.put(Material.IRON_AXE, 5.0);
        WEAPON_DAMAGE_18.put(Material.STONE_AXE, 4.0);
        WEAPON_DAMAGE_18.put(Material.GOLDEN_AXE, 3.0);
        WEAPON_DAMAGE_18.put(Material.WOODEN_AXE, 3.0);
        WEAPON_DAMAGE_18.put(Material.NETHERITE_AXE, 7.0);
    }

    public DamageHandler(CombatManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!manager.isEnabled())
            return;

        // Only handle player vs player combat
        if (!(event.getDamager() instanceof Player attacker))
            return;
        if (!(event.getEntity() instanceof Player victim))
            return;

        // Only apply in duels
        if (!manager.isInDuel(attacker))
            return;

        // Cancel sweeping edge damage
        if (manager.isRemoveSweeping() &&
                event.getCause() == EntityDamageByEntityEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            event.setCancelled(true);
            return;
        }

        double finalDamage = event.getDamage();

        // Remove critical hit bonus
        if (manager.isRemoveCrits()) {
            finalDamage = removeCriticalHitBonus(attacker, finalDamage);
        }

        // Normalize to 1.8 weapon damage values
        if (manager.isUse18DamageValues()) {
            finalDamage = normalize18Damage(attacker, finalDamage);
        }

        // Check if victim is blocking (sword block)
        if (manager.getBlockHandler().isBlocking(victim)) {
            double reduction = manager.getBlockDamageReduction();
            finalDamage *= (1.0 - reduction);
            manager.getBlockHandler().onHitWhileBlocking(victim);
        }

        event.setDamage(finalDamage);
    }

    /**
     * Remove the critical hit damage bonus.
     * In vanilla 1.9+, crits apply a 1.5x multiplier when falling and not on
     * ground.
     */
    private double removeCriticalHitBonus(Player attacker, double damage) {
        // Check if this was a critical hit (player falling, not on ground, not in
        // water, etc.)
        // Using velocity Y < 0 and fall distance > 0 as a reliable ground check
        boolean isOnGround = attacker.getLocation().getBlock().getRelative(org.bukkit.block.BlockFace.DOWN).getType()
                .isSolid()
                || attacker.getVelocity().getY() >= 0;

        if (attacker.getFallDistance() > 0.0 &&
                !isOnGround &&
                !attacker.isInWater() &&
                !attacker.isClimbing() &&
                attacker.getVehicle() == null) {
            // Vanilla applied 1.5x, we reverse it
            // However, we need to check if the damage already includes crit bonus
            // We'll just normalize instead
            return damage / 1.5;
        }
        return damage;
    }

    /**
     * Normalize damage to 1.8 weapon values.
     * This overrides the modern damage calculation with legacy values.
     */
    private double normalize18Damage(Player attacker, double currentDamage) {
        ItemStack weapon = attacker.getInventory().getItemInMainHand();

        if (weapon == null || weapon.getType() == Material.AIR) {
            return 1.0; // Fist damage
        }

        Double baseDamage = WEAPON_DAMAGE_18.get(weapon.getType());
        if (baseDamage == null) {
            // Not a recognized weapon, keep current damage
            return currentDamage;
        }

        // Calculate Sharpness bonus (1.8 style: +1.25 per level)
        int sharpnessLevel = weapon.getEnchantmentLevel(Enchantment.SHARPNESS);
        double sharpnessBonus = sharpnessLevel > 0 ? (sharpnessLevel * 1.25) : 0;

        // Calculate Smite/Bane bonus (situational - simplified here)
        // In actual 1.8, these were effective only against specific mobs
        // For PvP, we ignore them

        return baseDamage + sharpnessBonus;
    }

    /**
     * Get the 1.8 base damage for a weapon.
     */
    public static double get18BaseDamage(Material material) {
        return WEAPON_DAMAGE_18.getOrDefault(material, 1.0);
    }
}
