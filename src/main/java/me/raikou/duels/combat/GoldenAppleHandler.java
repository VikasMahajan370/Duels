package me.raikou.duels.combat;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Handles Golden Apple behavior to match 1.8 mechanics.
 * 
 * 1.8 Golden Apple Effects:
 * - Regular: Absorption I (2 min), Regeneration II (5 sec)
 * - Enchanted: Absorption IV (2 min), Regeneration V (30 sec),
 * Resistance I (5 min), Fire Resistance I (5 min)
 * 
 * Also removes the modern gapple cooldown.
 */
public class GoldenAppleHandler implements Listener {

    private final CombatManager manager;

    // Effect durations in ticks (20 ticks = 1 second)
    private static final int TICKS_PER_SECOND = 20;

    // Regular Golden Apple
    private static final int ABSORPTION_DURATION = 2 * 60 * TICKS_PER_SECOND; // 2 minutes
    private static final int REGEN_DURATION = 5 * TICKS_PER_SECOND; // 5 seconds

    // Enchanted Golden Apple
    private static final int ENCHANTED_ABSORPTION_DURATION = 2 * 60 * TICKS_PER_SECOND; // 2 minutes
    private static final int ENCHANTED_REGEN_DURATION = 30 * TICKS_PER_SECOND; // 30 seconds
    private static final int ENCHANTED_RESISTANCE_DURATION = 5 * 60 * TICKS_PER_SECOND; // 5 minutes
    private static final int ENCHANTED_FIRE_RES_DURATION = 5 * 60 * TICKS_PER_SECOND; // 5 minutes

    public GoldenAppleHandler(CombatManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!manager.isEnabled())
            return;
        if (!manager.isGappleUse18Effects())
            return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Only in duels
        if (!manager.isInDuel(player))
            return;

        if (item.getType() == Material.GOLDEN_APPLE) {
            // Let the event complete, then apply our effects on next tick
            Bukkit.getScheduler().runTask(manager.getPlugin(), () -> {
                applyRegularGoldenApple(player);
            });
        } else if (item.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
            Bukkit.getScheduler().runTask(manager.getPlugin(), () -> {
                applyEnchantedGoldenApple(player);
            });
        }

        // Remove cooldown (1.9+ gapple cooldown)
        if (manager.isGappleRemoveCooldown()) {
            Bukkit.getScheduler().runTask(manager.getPlugin(), () -> {
                player.setCooldown(Material.GOLDEN_APPLE, 0);
                player.setCooldown(Material.ENCHANTED_GOLDEN_APPLE, 0);
            });
        }
    }

    /**
     * Apply 1.8 regular golden apple effects.
     */
    private void applyRegularGoldenApple(Player player) {
        // Clear existing effects that we'll replace
        player.removePotionEffect(PotionEffectType.ABSORPTION);
        player.removePotionEffect(PotionEffectType.REGENERATION);

        // Absorption I for 2 minutes
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.ABSORPTION,
                ABSORPTION_DURATION,
                0, // Level 0 = Absorption I
                false,
                true));

        // Regeneration II for 5 seconds
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.REGENERATION,
                REGEN_DURATION,
                1, // Level 1 = Regeneration II
                false,
                true));
    }

    /**
     * Apply 1.8 enchanted golden apple effects.
     */
    private void applyEnchantedGoldenApple(Player player) {
        // Clear existing effects
        player.removePotionEffect(PotionEffectType.ABSORPTION);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);

        // Absorption IV for 2 minutes
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.ABSORPTION,
                ENCHANTED_ABSORPTION_DURATION,
                3, // Level 3 = Absorption IV
                false,
                true));

        // Regeneration V for 30 seconds (1.8 had very strong regen)
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.REGENERATION,
                ENCHANTED_REGEN_DURATION,
                4, // Level 4 = Regeneration V
                false,
                true));

        // Resistance I for 5 minutes
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.RESISTANCE,
                ENCHANTED_RESISTANCE_DURATION,
                0, // Level 0 = Resistance I
                false,
                true));

        // Fire Resistance I for 5 minutes
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.FIRE_RESISTANCE,
                ENCHANTED_FIRE_RES_DURATION,
                0,
                false,
                true));
    }
}
