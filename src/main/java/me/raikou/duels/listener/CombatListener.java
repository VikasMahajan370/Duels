package me.raikou.duels.listener;

import me.raikou.duels.DuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Handles fishing rod mechanics for 1.8 PvP style combat.
 * 
 * Note: Attack cooldown and sweep attack handling has been moved to
 * CombatManager.
 * This listener now only handles fishing rod knockback behavior.
 */
public class CombatListener implements Listener {

    private final DuelsPlugin plugin;
    private final boolean enabled;
    private final Set<UUID> rodCooldown = new HashSet<>();

    public CombatListener(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("combat.legacy-pvp", true);

        if (enabled) {
            plugin.getLogger().info("1.8 Legacy PvP fishing rod mechanics enabled!");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Cancel the vanilla rod pull behavior completely when reeling catches a
     * player.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFish(PlayerFishEvent event) {
        if (!enabled)
            return;

        Player fisher = event.getPlayer();

        if (plugin.getDuelManager().getDuel(fisher) == null)
            return;

        if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            if (event.getCaught() instanceof Player target) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Vector current = target.getVelocity();
                    target.setVelocity(new Vector(0, Math.min(current.getY(), 0), 0));
                });

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Vector current = target.getVelocity();
                    target.setVelocity(new Vector(0, Math.min(current.getY(), 0), 0));
                }, 1L);
            }
        }
    }

    /**
     * Fishing rod knockback - PUSH players AWAY when hook hits them
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!enabled)
            return;

        if (event.getEntity() instanceof FishHook hook) {
            if (hook.getShooter() instanceof Player fisher) {
                if (plugin.getDuelManager().getDuel(fisher) == null)
                    return;

                Entity hitEntity = event.getHitEntity();
                if (hitEntity instanceof Player target && !target.equals(fisher)) {
                    if (rodCooldown.contains(target.getUniqueId()))
                        return;

                    applyRodKnockback(fisher, target);
                    applyRodHitEffect(target);

                    rodCooldown.add(target.getUniqueId());
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        rodCooldown.remove(target.getUniqueId());
                    }, 10L);
                }
            }
        }
    }

    private void applyRodKnockback(Player fisher, Player target) {
        Vector knockback = target.getLocation().toVector()
                .subtract(fisher.getLocation().toVector());
        knockback.setY(0);

        if (knockback.lengthSquared() > 0) {
            knockback.normalize();
        }

        // Get per-kit rod knockback values from CombatManager
        double horizontalStrength = 0.65; // Default increased rod knockback
        double verticalStrength = 0.40; // Default increased rod knockback

        if (plugin.getCombatManager() != null) {
            double[] rodKB = plugin.getCombatManager().getKnockbackHandler().getRodKnockbackForPlayer(fisher);
            horizontalStrength = rodKB[0];
            verticalStrength = rodKB[1];
        }

        Vector finalVelocity = knockback.multiply(horizontalStrength).setY(verticalStrength);

        Bukkit.getScheduler().runTask(plugin, () -> {
            target.setVelocity(finalVelocity);
        });
    }

    private void applyRodHitEffect(Player target) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            int originalNoDamageTicks = target.getNoDamageTicks();
            target.setNoDamageTicks(0);

            double originalHealth = target.getHealth();
            target.damage(0.001);

            if (target.getHealth() < originalHealth) {
                target.setHealth(
                        Math.min(originalHealth, target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()));
            }

            target.setNoDamageTicks(originalNoDamageTicks);
        });

        target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);

        for (Player nearby : target.getWorld().getPlayers()) {
            if (nearby.getLocation().distance(target.getLocation()) < 20) {
                nearby.playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 1.0f);
            }
        }
    }
}
